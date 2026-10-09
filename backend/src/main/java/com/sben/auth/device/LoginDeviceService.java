package com.sben.auth.device;

import com.sben.common.web.RequestInfo;
import com.sben.notification.email.AccountEmails;
import com.sben.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Remembers where each user signs in from and emails a security alert when a
 * sign-in comes from a new browser or network. A device is identified by
 * browser family + operating system + network prefix (IPv4 /24, IPv6 /64), so
 * browser updates or a changing address within the same network don't cause
 * false alarms. The very first sign-in is recorded without an alert.
 */
@Service
@RequiredArgsConstructor
public class LoginDeviceService {

    private final LoginDeviceRepository repository;
    private final AccountEmails accountEmails;

    /** Runs in its own transaction so a problem here can never block a valid sign-in. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSignIn(User user) {
        String ip = RequestInfo.clientIp();
        String ua = RequestInfo.userAgent();
        String device = describe(ua);
        String fingerprint = sha256(user.getId() + "|" + device + "|" + networkPrefix(ip));
        LocalDateTime now = LocalDateTime.now();

        repository.findByUserIdAndFingerprint(user.getId(), fingerprint).ifPresentOrElse(d -> {
            d.setLastSeenAt(now);
            d.setIpAddress(truncate(ip, 45));
            repository.save(d);
        }, () -> {
            boolean firstEver = !repository.existsByUserId(user.getId());
            repository.save(LoginDevice.builder().userId(user.getId()).fingerprint(fingerprint)
                    .userAgent(truncate(ua, 255)).ipAddress(truncate(ip, 45))
                    .firstSeenAt(now).lastSeenAt(now).build());
            if (!firstEver) accountEmails.newSignIn(user, ip, device);
        });
    }

    /** "Chrome on Windows" style description from a User-Agent header. */
    static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "Unknown device";
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari"
                : ua.contains("curl/") || ua.contains("PostmanRuntime") || ua.contains("node") ? "API client"
                : "Browser";
        String os = ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS X") || ua.contains("Macintosh") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : null;
        return os == null ? browser : browser + " on " + os;
    }

    static String networkPrefix(String ip) {
        if (ip == null) return "";
        if (ip.contains(":")) { // IPv6: first four groups (/64)
            String[] g = ip.split(":");
            return String.join(":", java.util.Arrays.copyOf(g, Math.min(4, g.length)));
        }
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) : ip;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
