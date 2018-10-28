using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Security.Cryptography;
using System.Threading;
using System.Windows.Forms;

namespace CloudBlackboard
{
    public partial class Form1 : Form
    {
        public static string mUser;
        public static string mPINBase64;

        static Form1 mInstance;
        public static MyClient mClient = new MyClient();
        static int mClientDBVersion = 0;
        static int mRequestInterval = 10 * 1000;
        static Random mRandom = new Random();
        static Dictionary<string, bool> mDict_NotesChanged = new Dictionary<string, bool>();
        string mConfigPath = AppDomain.CurrentDomain.BaseDirectory + "config.ini";

        public Form1()
        {
            InitializeComponent();
            mInstance = this;

            if (!File.Exists(mConfigPath))
            {
                MessageBox.Show("错误：没有找到配置文件！");
                System.Environment.Exit(0);
            }
        }

        private void Form1_Load(object sender, EventArgs e)
        {
            try
            {
                mClient.mServerIP = IniHelper.ProfileReadValue("CloudBlackboardSetting", "ServerIP", mConfigPath);
                mClient.mServerPort = Convert.ToInt32(IniHelper.ProfileReadValue("CloudBlackboardSetting", "ServerPort", mConfigPath));
            }
            catch
            {
                MessageBox.Show("错误：配置文件不可用！");
                System.Environment.Exit(0);
            }

            if(mClient.mServerIP == "" ||
                mClient.mServerPort == 0
                )
            {
                MessageBox.Show("错误：配置文件不可用！");
                System.Environment.Exit(0);
            }

            Form_Logon form_logon = new Form_Logon();
            if(form_logon.ShowDialog() == DialogResult.Cancel)
            {
                System.Environment.Exit(0);
            }

            Thread thread = new Thread(new ThreadStart(ThreadGetNewNotes))
            {
                IsBackground = true
            };
            thread.Start();
        }

        static string GenTabText(string text)
        {
            if(text.Length > 512)
            {
                text = text.Substring(0, 512);
            }
            text = text.Trim();

            text = text.Replace("* ", "");
            text = text.Replace("\n", " ");
            text = text.Replace("\r", "");
            text = text.Replace("\t", " ");
            text = text.Replace("\b", "");
            text = text.Replace("  ", " ");
            text = text.Replace("  ", " ");
            text = text.Replace("  ", " ");

            if (text.Length == 0)
            {
                return "(empty)";
            }
            else if (text.Length > 10)
            {
                return text.Substring(0, 8) + " ...";
            }
            else
            {
                return text;
            }
        }

        static TextBox GenMyTextBox(string name, string text)
        {
            return new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = true,
                ScrollBars = ScrollBars.Vertical,
                Text = text,
                Name = name,
                Font = new Font("宋体", 10.5f)
            };
        }

        static Action<JObject> mAsyncUIDelegate_GetNewNotes = delegate (JObject respond)
        {
            double time_version = 0;
            JObject new_notes = (JObject)respond["new_notes"];
            JArray deleted_notes = (JArray)respond["deleted"];

            if(deleted_notes.Count > 0)
            {
                foreach (string noteID in deleted_notes)
                {
                    if (mDict_NotesChanged.ContainsKey(noteID))
                    {
                        if (mDict_NotesChanged[noteID])
                        {
                            //不更新正在编辑的note
                            continue;
                        }
                        else
                        {
                            mDict_NotesChanged.Remove(noteID);
                        }
                    }

                    foreach (TabPage p in mInstance.tabControl1.TabPages)
                    {
                        if (p.Name == noteID)
                        {
                            mInstance.tabControl1.TabPages.Remove(p);
                            break;
                        }
                    }
                }
            }

            if (new_notes.Count > 0)
            {
                foreach (var note in new_notes)
                {
                    string noteID = note.Key;
                    string text = (string)note.Value["text"];
                    bool noteExist = false;

                    if (mDict_NotesChanged.ContainsKey(noteID))
                    {
                        if (mDict_NotesChanged[noteID])
                        {
                            //不更新正在编辑的note
                            continue;
                        }
                    }
                    else
                    {
                        mDict_NotesChanged[noteID] = false;
                    }

                    double modify_time = (double)note.Value["time"];
                    if (modify_time > time_version)
                    {
                        time_version = modify_time;
                    }

                    foreach (TabPage p in mInstance.tabControl1.TabPages)
                    {
                        if (p.Name == noteID)
                        {
                            noteExist = true;
                            foreach (Control c in p.Controls)
                            {
                                if (c.GetType() == typeof(TextBox))
                                {
                                    c.TextChanged -= mInstance.tb_TextChanged;
                                    c.Text = text;
                                    c.TextChanged += mInstance.tb_TextChanged;
                                    break;
                                }
                            }

                            p.Text = "* " + GenTabText(text);
                            break;
                        }
                    }

                    if (!noteExist)
                    {
                        TabPage page = new TabPage
                        {
                            Name = noteID
                        };

                        page.Text = GenTabText(text);

                        TextBox tb = GenMyTextBox(noteID, text);
                        tb.TextChanged += mInstance.tb_TextChanged;
                        page.Controls.Add(tb);

                        mInstance.tabControl1.TabPages.Add(page);
                    }

                    mDict_NotesChanged[noteID] = false;
                }

                string theTime = DateTime.Parse("1970-1-1").AddSeconds((int)time_version).ToLocalTime().ToString();
                mInstance.toolSSL_UpdateTime.Text = "数据版本：" + theTime;
            }
        };

        static Action<string> mAsyncUIDelegate_Connection = delegate (string s)
        {
            mInstance.toolSSL_ConnectionText.Text = s;
            if(s == "已连接")
            {
                mInstance.toolSSL_ConnectionLight.BackColor = Color.LightGreen;
            }
            else if (s == "通讯中")
            {
                mInstance.toolSSL_ConnectionLight.BackColor = Color.GreenYellow;
            }
            else if (s == ("等待上传"))
            {
                mInstance.toolSSL_ConnectionLight.BackColor = Color.LightSkyBlue;
            }
            else if (s == "连接中")
            {
                mInstance.toolSSL_ConnectionLight.BackColor = Color.Yellow;
            }
            else if (s.Contains("登录失败"))
            {
                mInstance.toolSSL_ConnectionLight.BackColor = Color.IndianRed;
            }
        };

        class PutNoteParam
        {
            public string noteID;
            public string text;
            public string filePath;
        }

        static void ThreadPutNote(object obj)
        {
            PutNoteParam param = (PutNoteParam)obj;

            mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "等待上传" });

            if (mClient.PutNote(param.noteID, param.text, param.filePath))
            {
                mDict_NotesChanged[param.noteID] = false;
                mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "已连接" });
            }
            else
            {
                MessageBox.Show("上传数据失败！", "云白板", MessageBoxButtons.OK, MessageBoxIcon.Error);
                mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "连接中" });
            }
        }

        static void ThreadGetNewNotes()
        {
            while (true)
            {
                mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "连接中" });

                if (!mClient.Connect())
                {
                    Thread.Sleep(mRequestInterval);
                    continue;
                }

                if(!mClient.Logon(mUser, mPINBase64))
                {
                    mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "登录失败" });
                    Thread.Sleep(mRequestInterval);
                    continue;
                }

                mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "已连接" });

                while (true)
                {
                    mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "通讯中" });

                    JObject respond = mClient.GetNewNotes(ref mClientDBVersion);
                    if (respond == null)
                    {
                        Thread.Sleep(mRequestInterval);
                        break;
                    }

                    mInstance.tabControl1.Invoke(mAsyncUIDelegate_GetNewNotes, new object[] { respond });

                    mInstance.tabControl1.Invoke(mAsyncUIDelegate_Connection, new object[] { "已连接" });
                    Thread.Sleep(mRequestInterval);
                }
            }
        }

        private void Form1_FormClosing(object sender, FormClosingEventArgs e)
        {
            if(toolSSL_ConnectionText.Text == "连接中" || toolSSL_ConnectionText.Text == "登录失败")
            {
            }
            else
            {
                this.WindowState = FormWindowState.Minimized;
                e.Cancel = true;
            }
        }

        private void 退出ToolStripMenuItem_Click(object sender, EventArgs e)
        {
            notifyIcon1.Visible = false;
            System.Environment.Exit(0);
        }

        private void notifyIcon1_DoubleClick(object sender, EventArgs e)
        {
            this.Visible = true;
            this.WindowState = FormWindowState.Normal;
            this.TopMost = true;
            this.TopMost = false;
            this.Activate();
        }

        protected void tb_TextChanged(object sender, EventArgs e)
        {
            TextBox tb = (TextBox)sender;
            mDict_NotesChanged[tb.Name] = true;
            tb.Parent.Text = "> " + GenTabText(tb.Text);
        }

        private void toolStripStatusLabel2_Click(object sender, EventArgs e)
        {
            MD5 md5 = MD5.Create();
            byte[] rands = new byte[8];
            mRandom.NextBytes(rands);
            byte[] dt = System.Text.Encoding.Default.GetBytes(DateTime.Now.ToString());
            byte[] tmp = new byte[rands.Length + dt.Length];
            Array.Copy(rands, tmp, rands.Length);
            Array.Copy(dt, 0, tmp, rands.Length, dt.Length);
            string hash = ByteToHexString(md5.ComputeHash(tmp)).Substring(0, 8);

            TabPage page = new TabPage
            {
                Name = hash,
                Text = "(empty)"
            };

            TextBox tb = GenMyTextBox(hash, "");
            tb.TextChanged += tb_TextChanged;
            page.Controls.Add(tb);

            mInstance.tabControl1.TabPages.Add(page);
            mInstance.tabControl1.SelectedTab = page;

            mDict_NotesChanged[hash] = true;
        }

        private static string ByteToHexString(byte[] InBytes)
        {
            string StringOut = "";
            foreach (byte InByte in InBytes)
            {
                StringOut = StringOut + String.Format("{0:x2}", InByte);
            }
            return StringOut;
        }

        private void toolStripStatusLabel3_Click(object sender, EventArgs e)
        {
            if(tabControl1.SelectedTab == null)
            {
                return;
            }
            if(tabControl1.SelectedTab.Text == "[等待上传]")
            {
                return;
            }

            if (MessageBox.Show("确认删除："+ tabControl1.SelectedTab.Text, "删除确认",
                MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
            {
                if (mClient.DeleteNote(tabControl1.SelectedTab.Name))
                {
                    mDict_NotesChanged.Remove(tabControl1.SelectedTab.Name);
                    tabControl1.TabPages.Remove(tabControl1.SelectedTab);
                }
            }
        }

        private void tabControl1_SelectedIndexChanged(object sender, EventArgs e)
        {
            if (tabControl1.SelectedTab == null)
            {
                return;
            }

            if (tabControl1.SelectedTab.Text.Length > 1)
            {
                if (tabControl1.SelectedTab.Text.Substring(0, 2) == "* ")
                {
                    tabControl1.SelectedTab.Text = tabControl1.SelectedTab.Text.Substring(2);
                }
            }
        }

        private void Form1_Deactivate(object sender, EventArgs e)
        {
            if (tabControl1.SelectedTab == null)
            {
                return;
            }

            foreach (Control c in tabControl1.SelectedTab.Controls)
            {
                if(c.GetType() == typeof(TextBox))
                {
                    if(mDict_NotesChanged[c.Name])
                    {
                        tabControl1.SelectedTab.Text = "[等待上传]";

                        PutNoteParam p = new PutNoteParam
                        {
                            noteID = c.Name,
                            text = c.Text,
                            filePath = null
                        };

                        Thread thread = new Thread(new ParameterizedThreadStart(ThreadPutNote))
                        {
                            IsBackground = true
                        };
                        thread.Start(p);
                    }
                }
                break;
            }
        }

        private void tabControl1_Deselecting(object sender, TabControlCancelEventArgs e)
        {
            if(tabControl1.SelectedTab == null)
            {
                return;
            }

            foreach (Control c in tabControl1.SelectedTab.Controls)
            {
                if (c.GetType() == typeof(TextBox))
                {
                    if (mDict_NotesChanged[c.Name])
                    {
                        tabControl1.SelectedTab.Text = "[等待上传]";

                        PutNoteParam p = new PutNoteParam
                        {
                            noteID = c.Name,
                            text = c.Text,
                            filePath = null
                        };

                        Thread thread = new Thread(new ParameterizedThreadStart(ThreadPutNote))
                        {
                            IsBackground = true
                        };
                        thread.Start(p);
                    }
                }
                break;
            }
        }
    }
}
