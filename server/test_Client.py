#!/user/bin/env python3
# -*- coding: utf-8 -*-
import base64
import hashlib
import json
import random
import time
import socket
import sys

from RC4 import RC4
from package_io import fun_receive_pack, fun_unpack_receive_data, fun_pack_send_data, fun_send_pack

user = 'firexmoon'
pin = 'DSinmSGza79KV/AGrSDYP+j8Frjw1e9WTAPYrmN8tDM='
sheet = 'default'

# 通讯根密钥
base_rc4_key = 'CloudBlackboard_rc4_base_key'

server_ip_port = ('127.0.0.1', 1986)
# server_ip_port = ('beautyli.cn', 16821)

rc4 = RC4()


def do_request(__request):
    request_data = json.dumps(__request).encode('utf-8')
    # 加密数据
    send_data = fun_pack_send_data(rc4, request_data)
    # 发送数据
    fun_send_pack(sock, send_data)

    # 接收数据
    r_data = fun_receive_pack(sock)
    if not r_data:
        return None
    # 解密数据
    data_unpack = fun_unpack_receive_data(rc4, r_data)
    if not data_unpack:
        return None

    # 解析json报文
    try:
        __respond = json.loads(data_unpack.decode('utf-8'))
    except json.decoder.JSONDecodeError:
        return None
    return __respond


def logon(__sock):
    # random
    b_rand = __sock.recv(8)
    if len(b_rand) < 8:
        return False
    # 计算挑战值
    rc4.set_key((base_rc4_key + pin).encode())
    b_enc_rand = rc4.do_enc(b_rand)
    enc_rand = base64.b64encode(b_enc_rand).decode()

    # logon
    __request = {
        'transaction': 'logon',
        'user': user,
        'challenge': enc_rand
    }
    data = json.dumps(__request, ensure_ascii=False).encode('utf-8')
    # 发送数据
    fun_send_pack(__sock, data)
    # 接收结果
    receive_data = fun_receive_pack(__sock)
    if not receive_data:
        return False
    # 解密数据
    data_unpack = fun_unpack_receive_data(rc4, receive_data)
    if not data_unpack:
        return False

    # 解析json报文
    try:
        __respond = json.loads(data_unpack.decode('utf-8'))
    except json.decoder.JSONDecodeError:
        return False

    session_key = base64.b64decode(__respond['session_key'].encode())
    rc4.set_key(session_key)
    return True


def register(__sock, __register_user, __register_pin_base64):
    # random
    b_rand = __sock.recv(8)
    if len(b_rand) < 8:
        return False
    # 计算挑战值
    rc4.set_key(base_rc4_key.encode())
    b_enc_rand = rc4.do_enc(b_rand)
    enc_rand = base64.b64encode(b_enc_rand).decode()
    # 计算密文PIN
    enc_pin = base64.b64encode(rc4.do_enc(__register_pin_base64.encode())).decode()
    # logon
    __request = {
        'transaction': 'register',
        'user': __register_user,
        'pin': enc_pin,
        'challenge': enc_rand
    }
    data = json.dumps(__request, ensure_ascii=False).encode('utf-8')
    # 发送数据
    fun_send_pack(__sock, data)
    # 接收结果
    receive_data = fun_receive_pack(__sock)
    # 解密数据
    data_unpack = fun_unpack_receive_data(rc4, receive_data)
    if not data_unpack:
        return False
    # 解析json报文
    try:
        __respond = json.loads(data_unpack.decode('utf-8'))
    except json.decoder.JSONDecodeError:
        return False
    print(__respond)


# transaction 包括：
#   register, 注册新用户
#   logon, 登陆
#   unregister, 注销用户
#   modify_pin, 修改密码
#   create_sheet, 新建Sheet
#   rename_sheet, 重命名Sheet
#   delete_sheet, 删除Sheet
#   put_note, 新建/更新Note
#   get_new_notes, 下载比指定版本新的所有Note
#   delete_note, 删除Note


if __name__ == '__main__':
    sock = socket.socket()
    sock.connect(server_ip_port)

    if not logon(sock):
        print('logon failed.')
        sys.exit()

    # logon成功
    while True:
        msg = input('>>> Enter for get, "p" for put, "r" for register: ').strip()

        if msg == '':
            request = {
                'transaction': 'get_new_notes',
                'sheet': 'default',
                'client_db_version': 0
            }

            respond = do_request(request)
            if not request:
                break

            if respond['result'] == 'success':
                for note_id in respond['new_notes']:
                    print()
                    print('--------------------------------')
                    print('TEXT:')
                    print(respond['new_notes'][note_id]['text'])
                    print('TIME:')
                    modify_time = respond['new_notes'][note_id]['time']
                    print(time.strftime("%Y/%m/%d %H:%M:%S", time.localtime(modify_time)))
            else:
                print('result: %s, reason: %s' % (respond['result'], respond['reason']))
            print('--------------------------------')
            print('')

        elif msg == 'p':
            text = input('>>> input text: ').strip()
            if not text:
                continue

            hash_obj = hashlib.md5()
            hash_obj.update(random.SystemRandom().randint(0, 2 ** 64).to_bytes(8, byteorder='big'))
            hash_obj.update(text.encode('utf-8'))
            note_id = hash_obj.hexdigest()[:8]

            request = {
                'transaction': 'put_note',
                'sheet': 'default',
                'note_id': note_id,
                'time': int(time.time()),
                'text': text
            }

            respond = do_request(request)
            if not request:
                break

            if respond['result'] == 'success':
                print('result: %s' % respond['result'])
            else:
                print('result: %s, reason: %s' % (respond['result'], respond['reason']))

        elif msg == 'r':
            register_user = input('>>> input User: ').strip()
            if not user:
                continue
            pin = input('>>> input PIN: ').strip()
            if not pin:
                continue

            hash_obj = hashlib.sha256()
            hash_obj.update(pin.encode())
            register_pin_base64 = base64.b64encode(hash_obj.digest()).decode()

            sock = socket.socket()
            sock.connect(server_ip_port)

            register(sock, register_user, register_pin_base64)
            sys.exit()
