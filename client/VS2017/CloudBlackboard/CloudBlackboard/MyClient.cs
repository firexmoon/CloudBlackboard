using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using zlib;

namespace CloudBlackboard
{
    public class MyClient
    {
        private RC4 mRC4 = new RC4();

        private const string mRC4_BaseKey = "CloudBlackboard_rc4_base_key";

        public string mServerIP = "beautyli.cn";
        public int mServerPort = 16821;

        static readonly int mConnectTimeout = 10 * 1000;

        Socket mSocket;

        public bool Connect()
        {
            lock (this)
            {
                if (mSocket != null)
                {
                    mSocket.Close();
                }
                mSocket = new Socket(SocketType.Stream, ProtocolType.Tcp);

                try
                {
                    //IPEndPoint end_point = new IPEndPoint(Dns.GetHostEntry(mServerIP).AddressList[0], mServerPort);
                    IPEndPoint end_point = new IPEndPoint(IPAddress.Parse(mServerIP), mServerPort);

                    IAsyncResult connResult = mSocket.BeginConnect(end_point, null, null);
                    connResult.AsyncWaitHandle.WaitOne(mConnectTimeout, true);
                    if (!connResult.IsCompleted)
                    {
                        mSocket.Close();
                        return false;
                    }
                }
                catch (Exception)
                {
                    mSocket.Close();
                    return false;
                }

                if (!mSocket.Connected)
                {
                    mSocket.Close();
                    return false;
                }

                mSocket.ReceiveTimeout = mConnectTimeout / 2;
                return true;
            }
        }

        int Receive(byte[] buf)
        {
            try
            {
                int r = mSocket.Receive(buf);
                if (r <= 0)
                {
                    mSocket.Close();
                }
                return r;
            }
            catch (Exception)
            {
                mSocket.Close();
                return -1;
            }
        }

        public bool Logon(string user, string pin)
        {
            lock (this)
            {
                if (!mSocket.Connected)
                {
                    return false;
                }

                byte[] rand_buffer = new byte[8];
                if (Receive(rand_buffer) < rand_buffer.Length)
                {
                    return false;
                }

                //拼接密钥
                byte[] rc4_key = System.Text.Encoding.UTF8.GetBytes(mRC4_BaseKey + pin);

                //加密随机数
                mRC4.SetKey(rc4_key);
                byte[] enc_rand = mRC4.DoEnc(rand_buffer);
                string challenge = Convert.ToBase64String(enc_rand);

                JObject request = new JObject
                {
                    ["transaction"] = "logon",
                    ["user"] = user,
                    ["challenge"] = challenge
                };

                string request_string = JsonConvert.SerializeObject(request);
                byte[] request_data = System.Text.Encoding.UTF8.GetBytes(request_string);

                if (!SendPack(request_data))
                {
                    return false;
                }

                JObject respond = DoRequest(null);
                if (respond == null)
                {
                    return false;
                }

                string result = (string)respond["result"];
                if (result != "success")
                    return false;

                byte[] session_key = Convert.FromBase64String((string)respond["session_key"]);
                mRC4.SetKey(session_key);
                return true;
            }
        }

        public bool Register(string user, string pin, ref string reason)
        {
            lock (this)
            {
                if (!mSocket.Connected)
                {
                    return false;
                }

                byte[] rand_buffer = new byte[8];
                if (Receive(rand_buffer) < rand_buffer.Length)
                {
                    return false;
                }

                //密钥
                byte[] rc4_key = System.Text.Encoding.UTF8.GetBytes(mRC4_BaseKey);

                //加密随机数
                mRC4.SetKey(rc4_key);
                byte[] enc_rand = mRC4.DoEnc(rand_buffer);
                string challenge = Convert.ToBase64String(enc_rand);

                //加密PIN
                string enc_pin = Convert.ToBase64String(mRC4.DoEnc(System.Text.Encoding.UTF8.GetBytes(pin)));

                JObject request = new JObject
                {
                    ["transaction"] = "register",
                    ["user"] = user,
                    ["pin"] = enc_pin,
                    ["challenge"] = challenge
                };

                string request_string = JsonConvert.SerializeObject(request);
                byte[] request_data = System.Text.Encoding.UTF8.GetBytes(request_string);
                if (!SendPack(request_data))
                {
                    return false;
                }

                JObject respond = DoRequest(null);
                if (respond == null)
                {
                    return false;
                }

                string result = (string)respond["result"];
                if(respond.ContainsKey("reason"))
                {
                    reason = (string)respond["reason"];
                }
                return result == "success";
            }
        }

        private JObject DoRequest(JObject request)
        {
            lock (this)
            {
                try
                {
                    if (!mSocket.Connected)
                    {
                        return null;
                    }

                    if(request != null)
                    {
                        string request_string = JsonConvert.SerializeObject(request);
                        byte[] request_data = System.Text.Encoding.UTF8.GetBytes(request_string);
                        byte[] send_data = PackSendData(request_data);
                        SendPack(send_data);
                    }

                    byte[] receive_data = ReceivePack();
                    if(null == receive_data)
                    {
                        return null;
                    }

                    byte[] respond_data = UnpackReceiveData(receive_data);
                    string respond_string = System.Text.Encoding.UTF8.GetString(respond_data);
                    JObject respond = (JObject)JsonConvert.DeserializeObject(respond_string);
                    return respond;
                }
                catch (Exception)
                {
                    return null;
                }
            }
        }

        public JObject GetNewNotes(ref int clientDBVersion)
        {
            JObject request = new JObject
            {
                ["transaction"] = "get_new_notes",
                ["sheet"] = "default",
                ["client_db_version"] = clientDBVersion,
            };

            JObject respond = DoRequest(request);
            if (null == respond)
            {
                return null;
            }

            string result = (string)respond["result"];
            if (result == "success")
            {
                clientDBVersion = (int)respond["ver"];
                return respond;
            }
            else
            {
                return null;
            }
        }

        public bool PutNote(string note_id, string text, string filePath = null)
        {
            JObject request = new JObject
            {
                ["transaction"] = "put_note",
                ["sheet"] = "default",
                ["note_id"] = note_id,
                ["text"] = text,
                ["time"] = Convert.ToInt32(DateTime.UtcNow.Subtract(DateTime.Parse("1970-1-1")).TotalSeconds)
            };

            JObject respond = DoRequest(request);
            if (null == respond)
            {
                return false;
            }

            string result = (string)respond["result"];
            if (result != "success")
            {
                return false;
            }
            return true;
        }

        public bool DeleteNote(string note_id)
        {
            JObject request = new JObject
            {
                ["transaction"] = "delete_note",
                ["sheet"] = "default",
                ["note_id"] = note_id
            };

            JObject respond = DoRequest(request);
            if (null == respond)
            {
                return false;
            }

            string result = (string)respond["result"];
            if (result != "success")
            {
                string reason = (string)respond["reason"];
                if (reason != "note not existed")
                {
                    return false;
                }
            }
            return true;
        }

        private static bool __MemoryCompare(byte[] b1, byte[] b2, int len)
        {
            if (b1.Length < len || b2.Length < len)
            {
                return false;
            }
            for (int i = 0; i < len; i++)
            {
                if (b1[i] != b2[i])
                {
                    return false;
                }
            }
            return true;
        }

        private byte[] ReceivePack()
        {
            try
            {
                if (!mSocket.Connected)
                {
                    return null;
                }

                byte[] pack_size_buffer = new byte[4];
                if (Receive(pack_size_buffer) < pack_size_buffer.Length)
                {
                    return null;
                }

                int pack_size = pack_size_buffer[0] +
                    pack_size_buffer[1] * 256 +
                    pack_size_buffer[2] * 256 * 256 +
                    pack_size_buffer[3] * 256 * 256 * 256;
                if (pack_size < 16 || pack_size > 1024 * 1024 * 32)
                {
                    return null;
                }

                byte[] receive_data = new byte[pack_size];
                int receive_len = 0;
                while (receive_len < pack_size)
                {
                    byte[] receive_buffer = new byte[pack_size - receive_len];
                    int temp_len = Receive(receive_buffer);
                    if (temp_len <= 0)
                    {
                        return null;
                    }
                    Array.Copy(receive_buffer, 0, receive_data, receive_len, temp_len);
                    receive_len += temp_len;
                }

                return receive_data;
            }
            catch (Exception)
            {
                return null;
            }
        }

        private bool SendPack(byte[] data)
        {
            try
            {
                if (!mSocket.Connected)
                {
                    return false;
                }

                int data_len = data.Length;
                byte[] b_data_len = new byte[4];
                b_data_len[0] = (byte)(data_len & 0xff);
                b_data_len[1] = (byte)((data_len >> 8) & 0xff);
                b_data_len[2] = (byte)((data_len >> 16) & 0xff);
                b_data_len[3] = (byte)((data_len >> 24) & 0xff);

                if (b_data_len.Length != mSocket.Send(b_data_len))
                {
                    return false;
                }
                if(data.Length != mSocket.Send(data))
                {
                    return false;
                }
                return true;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private byte[] UnpackReceiveData(byte[] data)
        {
            SHA256 sha256 = new SHA256Managed();
            int hash_size = sha256.HashSize / 8;
            //解密数据
            byte[] dec_data = mRC4.DoEnc(data);
            if (dec_data.Length > hash_size)
            {
                byte[] receive_data = new byte[dec_data.Length - hash_size];
                Array.Copy(dec_data, hash_size, receive_data, 0, receive_data.Length);
                //校验hash
                byte[] hash = sha256.ComputeHash(receive_data);
                sha256.Clear();
                if (__MemoryCompare(hash, dec_data, hash.Length))
                {
                    //解压缩
                    return DecompressData(receive_data);
                }
            }
            return null;
        }

        private byte[] PackSendData(byte[] data)
        {
            //压缩
            byte[] zip_data = CompressData(data);

            //计算hash
            SHA256 sha256 = new SHA256Managed();
            byte[] hash = sha256.ComputeHash(zip_data);
            sha256.Clear();

            byte[] send_data = new byte[hash.Length + zip_data.Length];
            hash.CopyTo(send_data, 0);
            zip_data.CopyTo(send_data, hash.Length);

            //加密数据
            byte[] enc_data = mRC4.DoEnc(send_data);
            return enc_data;
        }

        private byte[] CompressData(byte[] inData)
        {
            using (MemoryStream outMemoryStream = new MemoryStream())
            using (ZOutputStream outZStream = new ZOutputStream(outMemoryStream, zlibConst.Z_DEFAULT_COMPRESSION))
            using (Stream inMemoryStream = new MemoryStream(inData))
            {
                CopyStream(inMemoryStream, outZStream);
                outZStream.finish();
                return outMemoryStream.ToArray();
            }
        }

        private byte[] DecompressData(byte[] inData)
        {
            using (MemoryStream outMemoryStream = new MemoryStream())
            using (ZOutputStream outZStream = new ZOutputStream(outMemoryStream))
            using (Stream inMemoryStream = new MemoryStream(inData))
            {
                CopyStream(inMemoryStream, outZStream);
                outZStream.finish();
                return outMemoryStream.ToArray();
            }
        }

        private void CopyStream(System.IO.Stream input, System.IO.Stream output)
        {
            byte[] buffer = new byte[2048];
            int len;
            while ((len = input.Read(buffer, 0, buffer.Length)) > 0)
            {
                output.Write(buffer, 0, len);
            }
            output.Flush();
        }
    }
}
