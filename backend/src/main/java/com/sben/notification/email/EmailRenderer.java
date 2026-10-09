package com.sben.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Year;

/**
 * Renders {@link EmailContent} into one consistent, branded email: a
 * table-based layout with inline styles (what Gmail, Outlook and mobile
 * clients reliably support) and a plain-text alternative for clients and
 * filters that prefer it. Every value is HTML-escaped.
 */
@Component
public class EmailRenderer {

    private static final String BRAND = "#dc2626";
    private static final String BRAND_DARK = "#b91c1c";
    private static final String SUCCESS = "#16a34a";

    private final String appName;
    private final String frontendUrl;

    public EmailRenderer(@Value("${app.mail.app-name:Smart Blood Emergency Network}") String appName,
                         @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.appName = appName;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public record Rendered(String subject, String html, String text) {}

    public Rendered render(EmailContent c, EmailCategory category) {
        return new Rendered(c.subject(), html(c, category), text(c, category));
    }

    /** Absolute link into the web app, e.g. {@code link("/login")}. */
    public String link(String path) {
        return frontendUrl + (path.startsWith("/") ? path : "/" + path);
    }

    // ---------- HTML ----------

    private String html(EmailContent c, EmailCategory category) {
        String accent = switch (c.toneOrDefault()) {
            case SUCCESS -> SUCCESS;
            case ALERT -> BRAND_DARK;
            case NEUTRAL -> BRAND;
        };
        StringBuilder body = new StringBuilder();
        if (c.heading() != null) {
            body.append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:#0f172a;\">")
                .append(esc(c.heading())).append("</h1>");
        }
        for (String p : c.paragraphs()) {
            body.append("<p style=\"margin:0 0 14px;font-size:15px;line-height:1.6;color:#334155;\">")
                .append(escMultiline(p)).append("</p>");
        }
        if (c.code() != null) {
            body.append("<div style=\"margin:22px 0;padding:18px;border:2px dashed ").append(accent)
                .append(";border-radius:12px;background:#fef2f2;text-align:center;\">")
                .append("<div style=\"font-family:'Courier New',monospace;font-size:34px;font-weight:700;letter-spacing:8px;color:")
                .append(accent).append(";\">").append(esc(c.code())).append("</div>");
            if (c.codeNote() != null) {
                body.append("<div style=\"margin-top:6px;font-size:13px;color:#64748b;\">").append(esc(c.codeNote())).append("</div>");
            }
            body.append("</div>");
        }
        if (c.details().stream().anyMatch(d -> notBlank(d.value()))) {
            body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"margin:8px 0 18px;border-collapse:collapse;font-size:14px;\">");
            for (EmailContent.Detail d : c.details()) {
                if (!notBlank(d.value())) continue;
                body.append("<tr><td style=\"padding:8px 12px 8px 0;color:#64748b;white-space:nowrap;vertical-align:top;border-bottom:1px solid #f1f5f9;\">")
                    .append(esc(d.label()))
                    .append("</td><td style=\"padding:8px 0;color:#0f172a;font-weight:600;border-bottom:1px solid #f1f5f9;\">")
                    .append(escMultiline(d.value())).append("</td></tr>");
            }
            body.append("</table>");
        }
        String url = safeUrl(c.actionUrl());
        if (c.actionLabel() != null && url != null) {
            body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 18px;\"><tr>")
                .append("<td style=\"border-radius:10px;background:").append(accent).append(";\">")
                .append("<a href=\"").append(esc(url)).append("\" target=\"_blank\" rel=\"noopener\" ")
                .append("style=\"display:inline-block;padding:12px 22px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:10px;\">")
                .append(esc(c.actionLabel())).append("</a></td></tr></table>")
                .append("<p style=\"margin:0 0 14px;font-size:12px;line-height:1.5;color:#94a3b8;\">")
                .append("If the button doesn&#39;t work, copy this link into your browser:<br>")
                .append("<a href=\"").append(esc(url)).append("\" style=\"color:#64748b;word-break:break-all;\">")
                .append(esc(url)).append("</a></p>");
        }
        for (String p : c.afterAction()) {
            body.append("<p style=\"margin:0 0 12px;font-size:13px;line-height:1.6;color:#64748b;\">")
                .append(escMultiline(p)).append("</p>");
        }

        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><title>" + esc(c.subject()) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">" + esc(c.preheader()) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f1f5f9;padding:24px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + accent + ";padding:20px 28px;\">"
                + "<span style=\"font-size:17px;font-weight:700;color:#ffffff;\">" + esc(appName) + "</span></td></tr>"
                + "<tr><td style=\"padding:28px 28px 12px;\">" + body + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px 24px;border-top:1px solid #f1f5f9;font-size:12px;line-height:1.6;color:#94a3b8;\">"
                + esc(footer(category)) + "<br>This is an automated message, please do not reply.<br>&copy; "
                + Year.now().getValue() + " " + esc(appName)
                + "</td></tr></table></td></tr></table></body></html>";
    }

    // ---------- plain text ----------

    private String text(EmailContent c, EmailCategory category) {
        StringBuilder t = new StringBuilder();
        if (c.heading() != null) t.append(c.heading()).append("\n\n");
        c.paragraphs().forEach(p -> t.append(p).append("\n\n"));
        if (c.code() != null) {
            t.append("    ").append(c.code()).append('\n');
            if (c.codeNote() != null) t.append("    ").append(c.codeNote()).append('\n');
            t.append('\n');
        }
        boolean anyDetail = false;
        for (EmailContent.Detail d : c.details()) {
            if (notBlank(d.value())) {
                t.append(d.label()).append(": ").append(d.value()).append('\n');
                anyDetail = true;
            }
        }
        if (anyDetail) t.append('\n');
        String url = safeUrl(c.actionUrl());
        if (c.actionLabel() != null && url != null) t.append(c.actionLabel()).append(": ").append(url).append("\n\n");
        c.afterAction().forEach(p -> t.append(p).append("\n\n"));
        t.append("--\n").append(footer(category))
                .append("\nThis is an automated message, please do not reply.\n").append(appName).append('\n');
        return t.toString();
    }

    private String footer(EmailCategory category) {
        return switch (category) {
            case SECURITY, ACCOUNT -> "You are receiving this email because it concerns the security of your " + appName + " account.";
            case ANNOUNCEMENT -> "You are receiving this announcement as a registered member of " + appName + ".";
            default -> "You are receiving this because of activity on your " + appName
                    + " account. You can change which updates you receive in your profile.";
        };
    }

    // ---------- helpers ----------

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private static String escMultiline(String s) {
        return esc(s).replace("\r\n", "\n").replace("\n", "<br>");
    }

    /** Only absolute http(s) links make it into an email. */
    static String safeUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        return u.startsWith("https://") || u.startsWith("http://") ? u : null;
    }
}
