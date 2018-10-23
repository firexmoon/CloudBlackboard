package com.beautyli.app.cloudblackboard;

public class RC4 {
    private byte[] m_s_box = new byte[256];
    private int m_index_x = 0;
    private int m_index_y = 0;

    public void setKey(byte[] key)
    {
        m_index_x = 0;
        m_index_y = 0;
        for (int i = 0; i < 256; i++)
        {
            m_s_box[i] = (byte)i;
        }
        for (int i = 0, j = 0; i < 256; i++)
        {
            j = (j + (m_s_box[i] & 0xff) + (key[i % key.length] & 0xff)) % 256;
            byte b = m_s_box[i];
            m_s_box[i] = m_s_box[j];
            m_s_box[j] = b;
        }
    }

    public byte[] doEnc(byte[] data)
    {
        byte[] out_data = new byte[data.length];
        for (int i = 0, mid; i < out_data.length; i++)
        {
            m_index_x = (m_index_x + 1) % 256;
            m_index_y = (m_index_y + (m_s_box[m_index_x] & 0xff)) % 256;

            byte b = m_s_box[m_index_x];
            m_s_box[m_index_x] = m_s_box[m_index_y];
            m_s_box[m_index_y] = b;

            mid = ((m_s_box[m_index_x] & 0xff) + (m_s_box[m_index_y] & 0xff)) % 256;
            out_data[i] = (byte)(data[i] ^ m_s_box[mid]);
        }
        return out_data;
    }
}
