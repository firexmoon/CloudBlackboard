using System;
using System.Security.Cryptography;
using System.Windows.Forms;

namespace CloudBlackboard
{
    public partial class Form_Logon : Form
    {
        string mConfigPath = AppDomain.CurrentDomain.BaseDirectory + "config.ini";
        bool mPinLoaded = false;

        public Form_Logon()
        {
            InitializeComponent();
        }

        private void button1_Click(object sender, EventArgs e)
        {
            string pin_base64 = "";
            if (mPinLoaded)
            {
                pin_base64 = textBox_PIN.Text;
            }
            else
            {
                //计算pin的hash
                SHA256 sha256 = new SHA256Managed();
                byte[] hash = sha256.ComputeHash(System.Text.Encoding.UTF8.GetBytes(textBox_PIN.Text));
                sha256.Clear();
                //Base64
                pin_base64 = Convert.ToBase64String(hash);
            }

            try
            {
                IniHelper.ProfileWriteValue("CloudBlackboardSetting", "User", textBox_User.Text, mConfigPath);

                if (!checkBox1.Checked)
                {
                    IniHelper.ProfileWriteValue("CloudBlackboardSetting", "PIN", "", mConfigPath);
                }
                else
                {
                    IniHelper.ProfileWriteValue("CloudBlackboardSetting", "PIN", pin_base64, mConfigPath);
                }
            }
            catch
            {
            }

            Form1.mUser = textBox_User.Text;
            Form1.mPINBase64 = pin_base64;
        }

        private void Form_Logon_Load(object sender, EventArgs e)
        {
            try
            {
                textBox_User.Text = IniHelper.ProfileReadValue("CloudBlackboardSetting", "User", mConfigPath);
                textBox_PIN.Text = IniHelper.ProfileReadValue("CloudBlackboardSetting", "PIN", mConfigPath);
                if(textBox_PIN.Text != "")
                {
                    mPinLoaded = true;
                    checkBox1.Checked = true;
                }
            }
            catch
            {
                mPinLoaded = false;
                checkBox1.Checked = false;
            }
        }

        private void textBox_User_TextChanged(object sender, EventArgs e)
        {
            button1.Enabled = (textBox_PIN.Text.Length >= 6 && textBox_User.Text.Length >= 3);
            linkLabel1.Enabled = button1.Enabled;
        }

        private void textBox_PIN_TextChanged(object sender, EventArgs e)
        {
            mPinLoaded = false;
            button1.Enabled = (textBox_PIN.Text.Length >= 6 && textBox_User.Text.Length >= 3);
            linkLabel1.Enabled = button1.Enabled;
        }

        private void textBox_PIN_KeyPress(object sender, KeyPressEventArgs e)
        {
            if(e.KeyChar == '\n')
            {
                button1_Click(null, null);
            }
        }

        private void linkLabel1_LinkClicked(object sender, LinkLabelLinkClickedEventArgs e)
        {
            string pin_base64 = "";
            if (mPinLoaded)
            {
                pin_base64 = textBox_PIN.Text;
            }
            else
            {
                //计算pin的hash
                SHA256 sha256 = new SHA256Managed();
                byte[] hash = sha256.ComputeHash(System.Text.Encoding.UTF8.GetBytes(textBox_PIN.Text));
                sha256.Clear();
                //Base64
                pin_base64 = Convert.ToBase64String(hash);
            }

            if (!Form1.mClient.Connect())
            {
                MessageBox.Show("无法连接到服务器！", "注册 云白板", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string reason = "";
            if(!Form1.mClient.Register(textBox_User.Text, pin_base64, ref reason))
            {
                MessageBox.Show("操作失败：" + reason, "注册 云白板", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            else
            {
                MessageBox.Show("注册成功。", "注册 云白板", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        }
    }
}
