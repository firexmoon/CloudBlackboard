import hashlib
import zlib

# 最大包大小
max_pack_size = 1024 * 256


def fun_send_pack(sock, data):
    data_len = len(data)
    # 包长度
    b_data_len = bytes([
        data_len & 0xff,
        (data_len >> 8) & 0xff,
        (data_len >> 16) & 0xff,
        (data_len >> 24) & 0xff
    ])
    try:
        sock.sendall(b_data_len)
        sock.sendall(data)
        return True
    except ConnectionResetError:
        return False


def fun_receive_pack(sock):
    try:
        # 先接收头部
        b_pack_size = sock.recv(4)
        if len(b_pack_size) < 4:
            return None
        pack_size = b_pack_size[0] + \
            b_pack_size[1] * 256 + \
            b_pack_size[2] * (256 ** 2) + \
            b_pack_size[3] * (256 ** 3)
        if pack_size < 16 or pack_size > max_pack_size:
            return None
        # 接收数据
        receive_data = b''
        receive_len = 0
        while receive_len < pack_size:
            tmp_data = sock.recv(pack_size - receive_len)
            if not len(tmp_data):
                return None
            receive_len += len(tmp_data)
            receive_data += tmp_data
        if receive_len < pack_size:
            return None
        return receive_data
    except ConnectionResetError:
        return None


def fun_unpack_receive_data(rc4, data):
    # 解密数据
    dec_data = rc4.do_enc(data)
    # 校验hash
    hash_obj = hashlib.sha256()
    if len(dec_data) > hash_obj.digest_size:
        hash_obj.update(dec_data[hash_obj.digest_size:])
        if hash_obj.digest() == dec_data[:hash_obj.digest_size]:
            # 解压缩
            try:
                unzip_data = zlib.decompress(dec_data[hash_obj.digest_size:])
            except zlib.error:
                return None
            return unzip_data
    return None


def fun_pack_send_data(rc4, data):
    a = len(data)
    # 压缩
    zip_data = zlib.compress(data)
    b = len(zip_data)
    # 计算hash
    hash_obj = hashlib.sha256()
    hash_obj.update(zip_data)
    data_digest = hash_obj.digest()
    # 加密数据
    enc_data = rc4.do_enc(data_digest + zip_data)
    return enc_data
