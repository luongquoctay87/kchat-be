package com.kchat.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class MailTemplateRendererTest {

    private MailTemplateRenderer mailTemplateRenderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("templates/");
        htmlResolver.setSuffix(".html");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.addTemplateResolver(htmlResolver);
        mailTemplateRenderer = new MailTemplateRenderer(engine);
    }

    @Test
    void rendersPasswordResetOtpHtmlAndText() {
        MailTemplateRenderer.RenderedMail rendered = mailTemplateRenderer.renderOtp(
                MailTemplateRenderer.OtpMail.passwordReset(
                        "k-chat",
                        "k-chat",
                        "support@company.com",
                        "Nguyễn Văn A",
                        "742891",
                        30
                )
        );

        assertThat(rendered.html())
                .contains("742891", "Nguyễn Văn A", "Đặt lại mật khẩu", "cid:kchatLogo")
                .contains(">7</span>", ">4</span>", ">2</span>");
        assertThat(rendered.text())
                .contains("742891", "Nguyễn Văn A", "support@company.com", "Mã đặt lại mật khẩu");
    }

    @Test
    void rendersRegistrationOtpHtmlAndText() {
        MailTemplateRenderer.RenderedMail rendered = mailTemplateRenderer.renderOtp(
                MailTemplateRenderer.OtpMail.registration(
                        "k-chat",
                        "k-chat",
                        "support@company.com",
                        "Nguyễn Văn A",
                        "123456",
                        30
                )
        );

        assertThat(rendered.html())
                .contains("123456", "Xác minh đăng ký", "cid:kchatLogo");
        assertThat(rendered.text())
                .contains("123456", "Mã xác minh đăng ký");
    }
}
