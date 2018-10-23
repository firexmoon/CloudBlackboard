using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace CloudBlackboard
{
    class RC4
    {
        byte[] m_s_box = new byte[256];
        int m_index_x = 0;
        int m_index_y = 0;

        public void SetKey(byte[] key)
        {
            m_index_x = 0;
            m_index_y = 0;
            for (int i = 0; i < 256; i++)
            {
                m_s_box[i] = (byte)i;
            }
            for (int i = 0, j = 0; i < 256; i++)
            {
                j = (j + m_s_box[i] + key[i % key.Length]) % 256;
                byte b = m_s_box[i];
                m_s_box[i] = m_s_box[j];
                m_s_box[j] = b;
            }
        }

        public byte[] DoEnc(byte[] data)
        {
            byte[] buffer = new byte[data.Length];
            data.CopyTo(buffer, 0);
            for (int i = 0, mid; i < buffer.Length; i++)
            {
                m_index_x = (m_index_x + 1) % 256;
                m_index_y = (m_index_y + m_s_box[m_index_x]) % 256;
                
                byte b = m_s_box[m_index_x];
                m_s_box[m_index_x] = m_s_box[m_index_y];
                m_s_box[m_index_y] = b;
                
                mid = (m_s_box[m_index_x] + m_s_box[m_index_y]) % 256;
                buffer[i] ^= m_s_box[mid];
            }
            return buffer;
        }
    }
}
