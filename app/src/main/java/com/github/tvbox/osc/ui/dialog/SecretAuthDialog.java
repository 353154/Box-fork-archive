package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.LiveSecretManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;

public class SecretAuthDialog extends BaseDialog {
    private final EditText etPinInput;
    private final TextView btnVerifyPin;
    private final TextView tvPinStatus;
    private final ImageView ivSecretQr;
    private final TextView tvQrStatus;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private String mSessionId = null;
    private boolean isPolling = false;
    private OnAuthListener mListener = null;

    public interface OnAuthListener {
        void onUnlocked();
        void onCancel();
    }

    public void setOnAuthListener(OnAuthListener listener) {
        this.mListener = listener;
    }

    public SecretAuthDialog(@NonNull Context context) {
        super(context);
        setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_secret_auth);

        etPinInput = findViewById(R.id.etSecretPin);
        btnVerifyPin = findViewById(R.id.btnVerifyPin);
        tvPinStatus = findViewById(R.id.tvPinStatus);
        ivSecretQr = findViewById(R.id.ivSecretQr);
        tvQrStatus = findViewById(R.id.tvQrStatus);

        btnVerifyPin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyPin();
            }
        });

        startQrFlow();
    }

    private void verifyPin() {
        String pin = etPinInput.getText().toString().trim();
        if (pin.isEmpty()) {
            tvPinStatus.setText("请输入管理密码");
            return;
        }

        tvPinStatus.setText("正在连接云端验证...");
        btnVerifyPin.setEnabled(false);

        String baseUrl = LiveSecretManager.getBaseServerUrl();
        String authUrl = baseUrl + "/auth?secret=" + pin;

        OkGo.<String>get(authUrl)
                .tag("secret_auth_pin")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        btnVerifyPin.setEnabled(true);
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            int code = json.has("code") ? json.get("code").getAsInt() : 0;
                            if (code == 200 && json.has("token")) {
                                String token = json.get("token").getAsString();
                                handleUnlockSuccess(token, "密码验证成功，私密频道已解锁！");
                            } else {
                                String msg = json.has("msg") ? json.get("msg").getAsString() : "密码错误";
                                tvPinStatus.setText(msg);
                            }
                        } catch (Exception e) {
                            tvPinStatus.setText("解析服务端响应异常");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        btnVerifyPin.setEnabled(true);
                        tvPinStatus.setText("连接服务端鉴权失败，请检查网络");
                    }
                });
    }

    private void startQrFlow() {
        tvQrStatus.setText("正在获取安全二维码...");
        String baseUrl = LiveSecretManager.getBaseServerUrl();
        String sessionUrl = baseUrl + "/qr_session";

        OkGo.<String>get(sessionUrl)
                .tag("secret_auth_qr")
                .execute(new StringCallback() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("sid") && json.has("url")) {
                                mSessionId = json.get("sid").getAsString();
                                String unlockUrl = json.get("url").getAsString();
                                Bitmap qrBitmap = QRCodeGen.generateBitmap(unlockUrl, 300, 300, 1);
                                if (qrBitmap != null) {
                                    ivSecretQr.setImageBitmap(qrBitmap);
                                    tvQrStatus.setText("等待手机微信/浏览器扫码授权...");
                                    startPolling();
                                } else {
                                    tvQrStatus.setText("二维码生成失败");
                                }
                            }
                        } catch (Exception e) {
                            tvQrStatus.setText("获取二维码异常");
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        tvQrStatus.setText("无法连接到授权服务器");
                    }
                });
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling || mSessionId == null) {
                return;
            }
            String baseUrl = LiveSecretManager.getBaseServerUrl();
            String pollUrl = baseUrl + "/qr_poll?sid=" + mSessionId;

            OkGo.<String>get(pollUrl)
                    .tag("secret_auth_poll")
                    .execute(new StringCallback() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            try {
                                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                String status = json.has("status") ? json.get("status").getAsString() : "";
                                if ("unlocked".equals(status) && json.has("token")) {
                                    String token = json.get("token").getAsString();
                                    handleUnlockSuccess(token, "🎉 手机端已完成授权，私密频道已解锁！");
                                    return;
                                } else if ("expired".equals(status)) {
                                    tvQrStatus.setText("二维码已过期，请重新打开");
                                    stopPolling();
                                    return;
                                }
                            } catch (Exception ignored) {
                            }
                            if (isPolling) {
                                mHandler.postDelayed(pollRunnable, 1500);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            if (isPolling) {
                                mHandler.postDelayed(pollRunnable, 2000);
                            }
                        }
                    });
        }
    };

    private void startPolling() {
        isPolling = true;
        mHandler.postDelayed(pollRunnable, 1500);
    }

    private void stopPolling() {
        isPolling = false;
        mHandler.removeCallbacksAndMessages(null);
    }

    private void handleUnlockSuccess(String token, String toastMsg) {
        stopPolling();
        LiveSecretManager.unlock(token);
        Toast.makeText(getContext(), toastMsg, Toast.LENGTH_SHORT).show();
        if (mListener != null) {
            mListener.onUnlocked();
        }
        dismiss();
    }

    @Override
    public void dismiss() {
        stopPolling();
        OkGo.getInstance().cancelTag("secret_auth_pin");
        OkGo.getInstance().cancelTag("secret_auth_qr");
        OkGo.getInstance().cancelTag("secret_auth_poll");
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (mListener != null) {
            mListener.onCancel();
        }
        dismiss();
    }
}
