
class RC4:
    __s_box = []
    __index_x = 0
    __index_y = 0

    def set_key(self, key):
        self.__index_x = 0
        self.__index_y = 0
        x = 0
        self.__s_box = list(range(256))
        for i in range(256):
            x = (x + self.__s_box[i] + key[i % len(key)]) % 256
            self.__s_box[i], self.__s_box[x] = self.__s_box[x], self.__s_box[i]

    def do_enc(self, data):
        out = []
        for c in data:
            self.__index_x = (self.__index_x + 1) % 256
            self.__index_y = (self.__index_y + self.__s_box[self.__index_x]) % 256
            self.__s_box[self.__index_x], self.__s_box[self.__index_y] = self.__s_box[self.__index_y], self.__s_box[self.__index_x]
            out.append(c ^ self.__s_box[(self.__s_box[self.__index_x] + self.__s_box[self.__index_y]) % 256])
        return bytes(out)
