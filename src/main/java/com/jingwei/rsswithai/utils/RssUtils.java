package com.jingwei.rsswithai.utils;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * RSS 解析工具类，封装常用的RSS条目处理逻辑和边界情况处理
 */
public final class RssUtils {

    private RssUtils() {}

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_DATE_TIME
    );

    /**
     * 从元素中安全读取文本内容（按标签名），返回trim后的字符串或null
     */
    public static String getElementText(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    /**
     * 简单清理HTML标签，保留纯文本。非严格的HTML-to-text转换。
     */
    public static String cleanHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .trim();
    }

    /**
     * 尝试解析常见的RSS/Atom日期字符串。若无法解析则返回null。
     */
    public static LocalDateTime tryParsePubDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(dateStr.trim(), formatter);
                return zdt.toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
        }
        return null;
    }

    /**
     * 将一个RSS/Atom的条目Element转换为Article实体，内部处理边界情况：
     * - 优先使用content标签作为正文，若不存在则使用description
     * - title/guid/pubDate提供备用方案或默认值
     */
    public static Article buildArticleFromElement(Element item, RssSource source) {
        if (item == null || source == null) return null;

        // title
        String title = getElementText(item, "title");

        // link（兼容Atom可能使用href）
        String link = getElementText(item, "link");
        if ((link == null || link.isBlank())) {
            NodeList linkNodes = item.getElementsByTagName("link");
            if (linkNodes.getLength() > 0) {
                Element linkElement = (Element) linkNodes.item(0);
                link = linkElement.getAttribute("href");
            }
        }

        // guid 或 id
        String guid = getElementText(item, "guid");
        if (guid == null || guid.isBlank()) {
            guid = getElementText(item, "id");
        }

        // description / summary
        String description = getElementText(item, "description");
        if (description == null) {
            description = getElementText(item, "summary");
        }

        // content 优先：content:encoded -> content -> description
        String content = getElementText(item, "content:encoded");
        if (content == null) content = getElementText(item, "content");
        if (content == null) content = description;

        // author
        String author = getElementText(item, "author");
        if (author == null) {
            author = getElementText(item, "dc:creator");
        }

        // pubDate published updated
        String pubDateStr = getElementText(item, "pubDate");
        if (pubDateStr == null) {
            pubDateStr = getElementText(item, "published");
            if (pubDateStr == null) {
                pubDateStr = getElementText(item, "updated");
            }
        }
        LocalDateTime pubDate = tryParsePubDate(pubDateStr);
        if (pubDate == null) {
            pubDate = LocalDateTime.now(); // 回退为当前时间
        }

        // categories
        StringBuilder categories = new StringBuilder();
        NodeList categoryNodes = item.getElementsByTagName("category");
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            if (i > 0) categories.append(",");
            categories.append(categoryNodes.item(i).getTextContent().trim());
        }

        // title fallback: 若title为空，则尝试使用description的前100字符
        if (title == null || title.isBlank()) {
            if (description != null && !description.isBlank()) {
                String s = cleanHtml(description);
                title = s.length() > 120 ? s.substring(0, 120) + "..." : s;
            } else if (link != null) {
                title = link;
            } else {
                title = "(no title)";
            }
        } else {
            title = cleanHtml(title);
        }

        // guid fallback: 优先使用link作为唯一标识
        if (guid == null || guid.isBlank()) {
            guid = link != null ? link : String.valueOf(System.identityHashCode(item));
        }

        // clean description/content
        description = cleanHtml(description);
        if (content != null) content = cleanHtml(content);

        return Article.builder()
                .source(source)
                .title(title)
                .link(link != null ? link.trim() : null)
                .guid(guid)
                .description(description)
                .content(content)
                .author(author)
                .pubDate(pubDate)
                .categories(categories.toString())
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
