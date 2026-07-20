package com.xs.sheepaimall.util;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.xs.sheepaimall.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
 /* 腾讯云短信工具类
 * <p>
 * 开发阶段（secretId 为空）仅打印验证码到日志；
 * 上线前在 application-dev.yml 中填入 tencent.sms 真实密钥后自动启用真实发送。
 * </p>
 */
@Slf4j
@Component
public class SmsUtil {

    @Autowired
    private SmsProperties smsProperties;

    /**
     * 发送短信验证码
     *
     * @param phone 目标手机号
     * @param code  6位数字验证码
     */
    public void sendCode(String phone, String code) {
        if (!StringUtils.hasText(smsProperties.getSecretId())) {
            // 开发模式：仅打印日志
            log.info("【开发模式】短信验证码 phone={}, code={}（上线后填入 tencent.sms 配置即可自动发送）", phone, code);
            return;
        }
        // 生产模式：调用腾讯云 SDK 发送
        try {
            Credential cred = new Credential(smsProperties.getSecretId(), smsProperties.getSecretKey());
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            SmsClient client = new SmsClient(cred, smsProperties.getRegion(), clientProfile);
            SendSmsRequest req = new SendSmsRequest();
            req.setSmsSdkAppId(smsProperties.getSdkAppId());
            req.setSignName(smsProperties.getSignName());
            req.setTemplateId(smsProperties.getTemplateId());
            req.setPhoneNumberSet(new String[]{"+86" + phone});
            req.setTemplateParamSet(new String[]{code});

            SendSmsResponse resp = client.SendSms(req);
            log.info("短信发送结果 phone={}, code={}, requestId={}", phone, code, resp.getRequestId());
        } catch (TencentCloudSDKException e) {
            log.error("短信发送失败 phone={}, error={}", phone, e.getMessage(), e);
        }
    }
}
