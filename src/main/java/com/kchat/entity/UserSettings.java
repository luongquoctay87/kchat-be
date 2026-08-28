package com.kchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.kchat.common.util.QuietHours;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    private UUID userId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(nullable = false, length = 20)
    private String theme = "system";

    @Column(name = "font_size", nullable = false, length = 20)
    private String fontSize = "medium";

    @Column(name = "privacy_dm", nullable = false, length = 20)
    private String privacyDm = "everyone";

    @Column(name = "show_online", nullable = false)
    private boolean showOnline = true;

    @Column(name = "enter_to_send", nullable = false)
    private boolean enterToSend = true;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "local_cache_retention_days")
    private Integer localCacheRetentionDays;

    @Column(name = "default_disappearing_seconds")
    private Integer defaultDisappearingSeconds;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserSettings defaultsFor(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        return settings;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public void setPushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getPrivacyDm() {
        return privacyDm;
    }

    public void setPrivacyDm(String privacyDm) {
        this.privacyDm = privacyDm;
    }

    public boolean isShowOnline() {
        return showOnline;
    }

    public void setShowOnline(boolean showOnline) {
        this.showOnline = showOnline;
    }

    public boolean isEnterToSend() {
        return enterToSend;
    }

    public void setEnterToSend(boolean enterToSend) {
        this.enterToSend = enterToSend;
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(LocalTime quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(LocalTime quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
    }

    public Integer getLocalCacheRetentionDays() {
        return localCacheRetentionDays;
    }

    public void setLocalCacheRetentionDays(Integer localCacheRetentionDays) {
        this.localCacheRetentionDays = localCacheRetentionDays;
    }

    public Integer getDefaultDisappearingSeconds() {
        return defaultDisappearingSeconds;
    }

    public void setDefaultDisappearingSeconds(Integer defaultDisappearingSeconds) {
        this.defaultDisappearingSeconds = defaultDisappearingSeconds;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isQuietHoursActive(LocalTime now) {
        return QuietHours.isActive(quietHoursStart, quietHoursEnd, now);
    }

    /** Whether push should be delivered right now (global toggle + quiet hours). */
    public boolean shouldReceivePush(LocalTime now) {
        return pushEnabled && !isQuietHoursActive(now);
    }
}
