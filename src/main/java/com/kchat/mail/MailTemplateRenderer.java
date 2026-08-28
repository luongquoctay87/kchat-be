package com.kchat.mail;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;

@Component
public class MailTemplateRenderer {

    public static final String LOGO_CONTENT_ID = "kchatLogo";

    private static final Locale VI = Locale.forLanguageTag("vi");

    private final SpringTemplateEngine templateEngine;

    public MailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedMail renderOtp(OtpMail model) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("appName", model.appName());
        variables.put("companyName", model.companyName());
        variables.put("displayName", model.displayName());
        variables.put("otp", model.otp());
        variables.put("otpDigits", toDigits(model.otp()));
        variables.put("ttlMinutes", model.ttlMinutes());
        variables.put("year", Year.now().getValue());
        variables.put("logoSrc", "cid:" + LOGO_CONTENT_ID);

        Context context = new Context(VI);
        context.setVariables(variables);

        String html = templateEngine.process(
                new TemplateSpec(model.template(), TemplateMode.HTML),
                context
        );
        return new RenderedMail(html, buildPlainText(model));
    }

    private static List<String> toDigits(String otp) {
        if (otp == null || otp.isBlank()) {
            return List.of();
        }
        return otp.chars().mapToObj(c -> String.valueOf((char) c)).toList();
    }

    private static String buildPlainText(OtpMail model) {
        StringBuilder text = new StringBuilder();
        text.append(model.appName()).append(" — ").append(model.subjectSuffix()).append("\n\n");
        text.append("Xin chào ").append(model.displayName()).append(",\n\n");
        text.append("Mã OTP: ").append(model.otp()).append('\n');
        text.append("Hiệu lực: ").append(model.ttlMinutes()).append(" phút\n\n");
        text.append("Không chia sẻ mã này với bất kỳ ai.\n");
        text.append("Nếu bạn không yêu cầu, hãy bỏ qua email này.\n\n");
        text.append("© ").append(Year.now().getValue()).append(' ').append(model.companyName()).append('\n');
        if (StringUtils.hasText(model.supportEmail())) {
            text.append("Hỗ trợ: ").append(model.supportEmail()).append('\n');
        }
        text.append("Email tự động — vui lòng không trả lời.\n");
        return text.toString();
    }

    public record OtpMail(
            String template,
            String subjectSuffix,
            String appName,
            String companyName,
            String supportEmail,
            String displayName,
            String otp,
            int ttlMinutes
    ) {
        public static OtpMail registration(
                String appName,
                String companyName,
                String supportEmail,
                String displayName,
                String otp,
                int ttlMinutes
        ) {
            return new OtpMail(
                    "mail/registration-otp",
                    "Mã xác minh đăng ký",
                    appName,
                    companyName,
                    supportEmail,
                    displayName,
                    otp,
                    ttlMinutes
            );
        }

        public static OtpMail passwordReset(
                String appName,
                String companyName,
                String supportEmail,
                String displayName,
                String otp,
                int ttlMinutes
        ) {
            return new OtpMail(
                    "mail/password-reset-otp",
                    "Mã đặt lại mật khẩu",
                    appName,
                    companyName,
                    supportEmail,
                    displayName,
                    otp,
                    ttlMinutes
            );
        }
    }

    public record RenderedMail(String html, String text) {
    }
}
