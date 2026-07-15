package com.nettool.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    // UI 控件
    private Button btnTabTcp, btnTabSftp, btnTcpConnect, btnTcpSend, btnTcpReset, btnTcpQuit, btnSftpGet, btnSftpSave;
    private View panelTcp, panelSftp;
    private EditText etTcpIp, etTcpPort, etTcpSend, etSftpUri, etSftpPath;
    private TextView tvTcpRecv;
    private ImageView ivSftpImg;

    // 网络相关
    private Socket tcpSocket;
    private Thread tcpReadThread;
    private boolean isTcpConnected = false;
    private Bitmap downloadedBitmap;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化所有控件
        initViews();
        // 绑定 Tab 切换与校验
        setupTabAndValidators();
        // 绑定 TCP 与 SFTP 业务
        setupNetworkActions();
    }

    private void initViews() {
        btnTabTcp = findViewById(R.id.btn_tab_tcp);
        btnTabSftp = findViewById(R.id.btn_tab_sftp);
        panelTcp = findViewById(R.id.panel_tcp);
        panelSftp = findViewById(R.id.panel_sftp);

        etTcpIp = findViewById(R.id.et_tcp_ip);
        etTcpPort = findViewById(R.id.et_tcp_port);
        btnTcpConnect = findViewById(R.id.btn_tcp_connect);
        tvTcpRecv = findViewById(R.id.tv_tcp_recv);
        etTcpSend = findViewById(R.id.et_tcp_send);
        btnTcpSend = findViewById(R.id.btn_tcp_send);
        btnTcpReset = findViewById(R.id.btn_tcp_reset);
        btnTcpQuit = findViewById(R.id.btn_tcp_quit);

        etSftpUri = findViewById(R.id.et_sftp_uri);
        etSftpPath = findViewById(R.id.et_sftp_path);
        btnSftpGet = findViewById(R.id.btn_sftp_get);
        ivSftpImg = findViewById(R.id.iv_sftp_img);
        btnSftpSave = findViewById(R.id.btn_sftp_save);

        // 隐藏键盘关联：自动拉起与隐藏
        etTcpSend.setOnFocusChangeListener((v, hasFocus) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                if (hasFocus) {
                    imm.showSoftInput(etTcpSend, 0);
                } else {
                    imm.hideSoftInputFromWindow(etTcpSend.getWindowToken(), 0);
                }
            }
        });
    }

    private void setupTabAndValidators() {
        btnTabTcp.setOnClickListener(v -> {
            panelTcp.setVisibility(View.VISIBLE);
            panelSftp.setVisibility(View.GONE);
            btnTabTcp.setBackgroundColor(0xFF00ADB5);
            btnTabSftp.setBackgroundColor(0xFF333333);
        });

        btnTabSftp.setOnClickListener(v -> {
            panelTcp.setVisibility(View.GONE);
            panelSftp.setVisibility(View.VISIBLE);
            btnTabTcp.setBackgroundColor(0xFF333333);
            btnTabSftp.setBackgroundColor(0xFF00ADB5);
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validateTcpInputs(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etTcpIp.addTextChangedListener(watcher);
        etTcpPort.addTextChangedListener(watcher);
    }

    private void validateTcpInputs() {
        String ip = etTcpIp.getText().toString().trim();
        String port = etTcpPort.getText().toString().trim();
        Pattern ipPattern = Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
        Pattern portPattern = Pattern.compile("^([1-9]\\d{0,3}|[1-5]\\d{4}|6[0-4]\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$");

        btnTcpConnect.setEnabled(ipPattern.matcher(ip).matches() && portPattern.matcher(port).matches());
    }

    private void setupNetworkActions() {
        // TCP 连接
        btnTcpConnect.setOnClickListener(v -> {
            final String ip = etTcpIp.getText().toString().trim();
            final int port = Integer.parseInt(etTcpPort.getText().toString().trim());
            showToast("正在建立连接...");
            new Thread(() -> {
                try {
                    tcpSocket = new Socket();
                    tcpSocket.connect(new InetSocketAddress(ip, port), 5000);
                    isTcpConnected = true;
                    mainHandler.post(() -> {
                        showToast("连接成功！");
                        btnTcpConnect.setText("已连接");
                        btnTcpConnect.setEnabled(false);
                    });
                    startTcpReadLoop();
                } catch (Exception e) {
                    mainHandler.post(() -> showToast("连接失败: " + e.getMessage()));
                }
            }).start();
        });

        // TCP 发送
        btnTcpSend.setOnClickListener(v -> {
            final String data = etTcpSend.getText().toString();
            if (data.isEmpty() || !isTcpConnected) return;
            new Thread(() -> {
                try {
                    OutputStream out = tcpSocket.getOutputStream();
                    out.write(data.getBytes("UTF-8"));
                    out.flush();
                    mainHandler.post(() -> etTcpSend.setText(""));
                } catch (Exception e) {
                    mainHandler.post(() -> showToast("发送失败: " + e.getMessage()));
                }
            }).start();
        });

        // TCP 重置
        btnTcpReset.setOnClickListener(v -> {
            disconnectTcp();
            btnTcpConnect.performClick();
        });

        // TCP 退出
        btnTcpQuit.setOnClickListener(v -> disconnectTcp());

        // SFTP 获取图片
        btnSftpGet.setOnClickListener(v -> {
            final String uri = etSftpUri.getText().toString().trim();
            final String path = etSftpPath.getText().toString().trim();
            if (uri.isEmpty() || path.isEmpty()) {
                showToast("请完整填写 SFTP 参数及路径");
                return;
            }
            btnSftpGet.setEnabled(false);
            btnSftpGet.setText("正在下载并断开...");
            new Thread(() -> {
                Session session = null;
                ChannelSftp sftp = null;
                try {
                    // 解析 username:password@ip:port
                    String[] parts = uri.split("@");
                    String[] auth = parts[0].split(":");
                    String username = auth[0];
                    String password = auth[1];
                    String[] hostParts = parts[1].split(":");
                    String host = hostParts[0];
                    int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 22;

                    JSch jsch = new JSch();
                    session = jsch.getSession(username, host, port);
                    session.setPassword(password);
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.connect(10000);

                    sftp = (ChannelSftp) session.openChannel("sftp");
                    sftp.connect(10000);

                    InputStream in = sftp.get(path);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }

                    downloadedBitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
                    mainHandler.post(() -> {
                        if (downloadedBitmap != null) {
                            ivSftpImg.setImageBitmap(downloadedBitmap);
                            btnSftpSave.setVisibility(View.VISIBLE);
                            showToast("获取图片成功！");
                        } else {
                            showToast("图片解析失败");
                        }
                    });

                } catch (Exception e) {
                    mainHandler.post(() -> showToast("SFTP 异常: " + e.getMessage()));
                } finally {
                    if (sftp != null && sftp.isConnected()) sftp.disconnect();
                    if (session != null && session.isConnected()) session.disconnect();
                    mainHandler.post(() -> {
                        btnSftpGet.setEnabled(true);
                        btnSftpGet.setText("SFTP GET FILE");
                    });
                }
            }).start();
        });

        // 本地保存图片
        btnSftpSave.setOnClickListener(v -> {
            if (downloadedBitmap == null) return;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "sftp_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NetTools");
                }
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    downloadedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.close();
                    showToast("成功保存至相册！");
                }
            } catch (Exception e) {
                showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private void startTcpReadLoop() {
        tcpReadThread = new Thread(() -> {
            try {
                InputStream in = tcpSocket.getInputStream();
                byte[] buffer = new byte[1024];
                int len;
                while (isTcpConnected && (len = in.read(buffer)) != -1) {
                    final String msg = new String(buffer, 0, len, "UTF-8");
                    mainHandler.post(() -> tvTcpRecv.append(msg));
                }
            } catch (Exception e) {
                mainHandler.post(() -> showToast("连接中断: " + e.getMessage()));
            } finally {
                disconnectTcp();
            }
        });
        tcpReadThread.start();
    }

    private void disconnectTcp() {
        isTcpConnected = false;
        try {
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
        } catch (Exception ignored) {}
        mainHandler.post(() -> {
            btnTcpConnect.setText("连接");
            btnTcpConnect.setEnabled(true);
            validateTcpInputs();
        });
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
