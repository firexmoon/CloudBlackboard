#!/user/bin/env python3
# -*- coding: utf-8 -*-
import base64
import binascii
import time
import random
import socketserver
import json
import threading

from RC4 import RC4
from package_io import fun_send_pack, fun_receive_pack, fun_unpack_receive_data, fun_pack_send_data
from request_handler import request_handler

# 服务端口
server_port = 1986
# 定时器间隔（秒）
timer_dump_interval = 60
timer_register_limit_interval = 60 * 60 * 24
# 当前dump文件的版本
cur_dump_version = 0
# 当前数据的版本
cur_db_version = 0

# 通讯根密钥
base_rc4_key = 'CloudBlackboard_rc4_base_key'

# 线程同步锁
mutex_db = threading.Lock()

# 数据库文件
# db_file = '/app/myCloudBlackboard.db'
db_file = 'myCloudBlackboard.db'

# 内存中的数据库
myDB = {}

# 注册用户计数
register_user_count = 0
# 注册用户限制
register_user_count_limit = 30


def fun_load_db():
    global myDB
    mutex_db.acquire()
    try:
        with open(db_file, 'rb') as infile:
            # 从文件加载数据
            enc_json = infile.read()
            # 解密
            dump_rc4 = RC4()
            dump_rc4.set_key(base_rc4_key.encode())
            str_json = dump_rc4.do_enc(enc_json).decode('utf-8')
            # load
            myDB = json.loads(str_json)
            print('load DB from file success')
    except FileNotFoundError:
        print('load DB from file failed')
    mutex_db.release()


def fun_timer_register_limit():
    global timer_register_limit, register_user_count

    register_user_count = 0

    # 循环定时器
    timer_register_limit = threading.Timer(timer_register_limit_interval, fun_timer_register_limit)
    timer_register_limit.start()


def fun_timer_dump():
    global timer_dump, cur_dump_version

    mutex_db.acquire()
    # 比较dump版本和DB版本，决定是否需要dump数据到文件
    if cur_dump_version < cur_db_version:
        # dump
        json_str = json.dumps(myDB, ensure_ascii=False)
        # 加密
        dump_rc4 = RC4()
        dump_rc4.set_key(base_rc4_key.encode())
        enc_json = dump_rc4.do_enc(json_str.encode('utf-8'))
        # 写入文件
        with open(db_file, 'wb') as outfile:
            outfile.write(enc_json)
            # 更新dump版本
            cur_dump_version = cur_db_version
            print('fun_timer_dump dump done. cur_db_version: %d' % cur_db_version)
    mutex_db.release()

    # 循环定时器
    timer_dump = threading.Timer(timer_dump_interval, fun_timer_dump)
    timer_dump.start()


def fun_logon(rand, request):
    global myDB, register_user_count
    respond = {'result': 'failed', 'reason': 'bad request'}
    rc4 = None
    try:
        if request['transaction'] == 'logon':
            user = request['user']
            challenge = base64.b64decode(request['challenge'])
            pin = myDB[user]['pin']
            # 解密数据
            rc4 = RC4()
            rc4.set_key((base_rc4_key + pin).encode())
            enc_random = rc4.do_enc(rand)
            # 检验随机数
            if enc_random == challenge:
                respond = {'result': 'success', 'user': user}
            else:
                respond = {'result': 'failed', 'reason': 'bad challenge'}

        elif request['transaction'] == 'register':
            challenge = base64.b64decode(request['challenge'])
            # 解密数据
            rc4 = RC4()
            rc4.set_key(base_rc4_key.encode())
            enc_random = rc4.do_enc(rand)
            pin = (rc4.do_enc(base64.b64decode(request['pin']))).decode()
            # 检验随机数
            if enc_random != challenge:
                respond = {'result': 'failed', 'reason': 'bad challenge'}
            else:
                user = request['user']
                if len(user) < 3:
                    respond = {'result': 'failed', 'reason': 'user name too short'}
                else:
                    if user in myDB:
                        respond = {'result': 'failed', 'reason': 'user already registered'}
                    else:
                        if len(pin) < 6:
                            respond = {'result': 'failed', 'reason': 'pin too short'}
                        else:
                            if register_user_count >= register_user_count_limit:
                                respond = {'result': 'failed',
                                           'reason': 'reach the register_user_count limit in 24 hour'}
                            else:
                                myDB[user] = {
                                    'create_time': time.time(),
                                    'pin': pin,
                                    'box': {
                                        'default': {
                                            'ver': 0,
                                            'note': {},
                                            'deleted': {}
                                        }
                                    }
                                }
                                respond = {'result': 'success', 'user': user}
                                register_user_count += 1

    except KeyError as e:
        respond = {'result': 'failed', 'reason': 'KeyError with ' + str(e)}
    except binascii.Error:
        pass

    return respond, rc4


class MyServer(socketserver.BaseRequestHandler):
    # 处理一个连接
    def handle(self):
        global cur_db_version

        # print('connected from %s:%d' % (self.client_address[0], self.client_address[1]))
        sock = self.request
        finished = False

        # 发送随机数
        rand = random.SystemRandom().randint(0, 2 ** 64).to_bytes(8, byteorder='big')
        try:
            sock.sendall(rand)
        except ConnectionResetError:
            return
        # 接收第一个报文
        receive_data = fun_receive_pack(sock)
        if receive_data:
            # 解析json报文
            try:
                request = json.loads(receive_data.decode('utf-8'))
            except json.decoder.JSONDecodeError:
                return
            except UnicodeDecodeError:
                return
            # 验证用户登陆
            mutex_db.acquire()
            respond, rc4 = fun_logon(rand, request)
            mutex_db.release()
            transaction = request['transaction']
            # 注册新用户
            if transaction == 'register':
                if respond['result'] == 'success':
                    # 更新数据库版本
                    cur_db_version += 1
                    print('new register  user: %s, cur_db_version: %d' %
                          (respond['user'], cur_db_version))
                if rc4 is not None:
                    # json报文
                    respond_data = json.dumps(respond, ensure_ascii=False).encode('utf-8')
                    # 加密数据
                    send_data = fun_pack_send_data(rc4, respond_data)
                    # 发送数据
                    fun_send_pack(sock, send_data)
                # 断开连接
                return
            elif transaction == 'logon':
                if respond['result'] == 'success' and rc4 is not None:
                    # 登录成功
                    user = respond['user']
                    print('logon: %s' % user)
                    # 产生会话密钥
                    session_key = random.SystemRandom().randint(0, 2 ** 128).to_bytes(16, byteorder='big')
                    respond['session_key'] = base64.b64encode(session_key).decode()
                    # json报文
                    respond_data = json.dumps(respond, ensure_ascii=False).encode('utf-8')
                    # 加密数据
                    send_data = fun_pack_send_data(rc4, respond_data)
                    # 发送数据
                    if not fun_send_pack(sock, send_data):
                        return
                    # 更新密码机
                    rc4.set_key(session_key)
                    # 处理后续请求
                    while not finished:
                        receive_data = fun_receive_pack(sock)
                        if not receive_data:
                            break
                        # 解密数据
                        request_data = fun_unpack_receive_data(rc4, receive_data)
                        if not request_data:
                            break
                        # 解析json报文
                        try:
                            request = json.loads(request_data.decode('utf-8'))
                        except json.decoder.JSONDecodeError:
                            break
                        except UnicodeDecodeError:
                            break
                        # 处理请求
                        mutex_db.acquire()
                        respond, finished = request_handler(request, myDB[user])
                        mutex_db.release()
                        # 执行结果
                        if respond:
                            # 更新DB版本
                            if respond['result'] == 'success' and request['transaction'] != 'get_new_notes':
                                cur_db_version += 1

                            transaction = request['transaction']
                            if transaction != 'get_new_notes':
                                if respond['result'] == 'success':
                                    print('new request  user: %s, transaction: %s，result: %s, cur_db_version: %d' %
                                          (user, transaction, respond['result'], cur_db_version))
                                elif 'reason' in respond:
                                    print('new request  user: %s, transaction: %s，result: %s, reason:%s' %
                                          (user, transaction, respond['result'], respond['reason']))

                            # json报文
                            respond_data = json.dumps(respond, ensure_ascii=False).encode('utf-8')
                            # 加密数据
                            send_data = fun_pack_send_data(rc4, respond_data)
                            # 发送数据
                            if not fun_send_pack(sock, send_data):
                                break
        sock.close()
        # print('disconnected from %s:%d' % (self.client_address[0], self.client_address[1]))


if __name__ == '__main__':

    # load DB
    fun_load_db()

    # 清理数据库
    for __user in myDB:
        for __sheet in myDB[__user]['box']:
            myDB[__user]['box'][__sheet]['deleted'] = {}

    # 启动定时器任务
    timer_dump = threading.Timer(timer_dump_interval, fun_timer_dump)
    timer_dump.start()

    # 启动定时器任务
    timer_register_limit = threading.Timer(timer_register_limit_interval, fun_timer_register_limit)
    timer_register_limit.start()

    # 启动Server
    obj = socketserver.ThreadingTCPServer(('0.0.0.0', server_port), MyServer)
    obj.serve_forever()
