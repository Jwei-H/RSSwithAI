package com.jingwei.rsswithai.utils;

import com.github.htmltomd.ConverterConfig;
import com.github.htmltomd.HtmlToMarkdownConverter;
import com.github.htmltomd.handler.ElementHandler;
import com.github.htmltomd.handler.HandlerContext;
import com.github.htmltomd.handler.impl.ParagraphHandler;
import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.model.RssSource;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSS/Atom 统一解析工具类
 * 自动检测并解析 RSS 2.0、RSS 1.0、Atom 格式，调用者无需关心具体格式
 */
@Slf4j
public final class RssUtils {

    private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("font-size\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)px", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_SEPARATOR_PATTERN = Pattern.compile("[|｜丨/\\-]");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss  Z"), // 针对报错中的双空格格式
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
    );

    private static final HtmlToMarkdownConverter converter = new HtmlToMarkdownConverter(ConverterConfig.builder()
            .addCustomHandler("p", new ElementHandler() {
                private final ParagraphHandler defaultHandler = new ParagraphHandler();

                @Override
                public String handle(org.jsoup.nodes.Element element, HandlerContext context) {
                    String className = element.className();
                    String style = element.attr("style");
                    String text = context.processChildren(element).trim();

                    if (isBlank(text)) {
                        return defaultHandler.handle(element, context);
                    }

                    if (className.contains("text-big-title")) {
                        return "## " + text + "\n\n";
                    }
                    if (className.contains("text-sm-title")) {
                        return "### " + text + "\n\n";
                    }

                    boolean isCentered = style.contains("text-align: center") || style.contains("text-align:center");
                    boolean hasBold = !element.select("strong, b").isEmpty()
                            || style.contains("font-weight: bold")
                            || style.contains("font-weight:bold")
                            || style.contains("font-weight: 700")
                            || style.contains("font-weight:700")
                            || !element.select("span[style*='font-weight: bold'], span[style*='font-weight:bold'], span[style*='font-weight: 700'], span[style*='font-weight:700']").isEmpty();

                    if (isCentered && hasBold && isLikelyHeadingByStyleAndText(element, text)) {
                        return "# " + text + "\n\n";
                    }
                    return defaultHandler.handle(element, context);
                }
            })
            .build());

    private RssUtils() {
    }

    /**
     * 解析RSS/Atom的Channel元信息（title、description、link）
     *
     * @param xmlContent XML内容字符串
     * @return Channel元信息，如果解析失败返回null
     */
    public static ChannelInfo parseChannelInfo(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            return null;
        }

        try {
            Document doc = parseXmlDocument(xmlContent);
            FeedFormat format = detectFeedFormat(doc);
            Element root = doc.getDocumentElement();

            String title = null;
            String description = null;
            String link = null;

            if (format == FeedFormat.ATOM) {
                // Atom格式: <feed><title>、<subtitle>、<link>
                title = getElementText(root, "title");
                description = getElementText(root, "subtitle");
                // Atom的link可能有多个，取rel="alternate"或第一个
                NodeList linkNodes = root.getElementsByTagName("link");
                for (int i = 0; i < linkNodes.getLength(); i++) {
                    Element linkEl = (Element) linkNodes.item(i);
                    String href = linkEl.getAttribute("href");
                    String rel = linkEl.getAttribute("rel");
                    if ("alternate".equals(rel) || isBlank(rel)) {
                        link = href;
                        break;
                    }
                }
                if (link == null && linkNodes.getLength() > 0) {
                    Element linkEl = (Element) linkNodes.item(0);
                    link = linkEl.getAttribute("href");
                }
            } else {
                // RSS格式: <rss><channel><title>、<description>、<link>
                NodeList channelNodes = doc.getElementsByTagName("channel");
                if (channelNodes.getLength() > 0) {
                    Element channel = (Element) channelNodes.item(0);
                    title = getElementText(channel, "title");
                    description = getElementText(channel, "description");
                    link = getElementText(channel, "link");
                }
            }

            // 清理HTML标签
            if (title != null) {
                title = cleanHtml(title);
            }
            if (description != null) {
                description = cleanHtml(description);
            }

            return new ChannelInfo(title, description, link);

        } catch (Exception e) {
            log.error("解析Channel元信息失败: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 轻量解析入口：只解析条目字段，不执行HTML转Markdown等重操作
     */
    public static List<ParsedItem> parseItems(String xmlContent, RssSource source) {
        List<ParsedItem> items = new ArrayList<>();

        if (xmlContent == null || xmlContent.isBlank() || source == null) {
            return items;
        }

        try {
            Document doc = parseXmlDocument(xmlContent);
            FeedFormat format = detectFeedFormat(doc);
            log.debug("检测到Feed格式: {}, source={}", format, source.getName());

            NodeList itemList = getItemNodes(doc, format);

            for (int i = 0; i < itemList.getLength(); i++) {
                Element item = (Element) itemList.item(i);
                ParsedItem parsedItem = buildParsedItem(item, format);
                if (parsedItem != null) {
                    items.add(parsedItem);
                }
            }

            log.debug("轻量解析完成: source={}, format={}, 条目数={}", source.getName(), format, items.size());

        } catch (Exception e) {
            log.error("解析RSS/Atom内容失败: source={}, error={}", source.getName(), e.getMessage());
        }

        return items;
    }

    /**
     * 解析XML文档，配置安全设置防止XXE攻击
     */
    private static Document parseXmlDocument(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // 安全设置，防止XXE攻击
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * 自动检测Feed格式
     */
    private static FeedFormat detectFeedFormat(Document doc) {
        Element root = doc.getDocumentElement();
        String rootName = root.getTagName().toLowerCase();

        // Atom格式: <feed>
        if (rootName.equals("feed") || rootName.endsWith(":feed")) {
            return FeedFormat.ATOM;
        }

        // RSS 2.0格式: <rss><channel><item>
        if (rootName.equals("rss")) {
            return FeedFormat.RSS_2_0;
        }

        // RSS 1.0 / RDF格式: <rdf:RDF>
        if (rootName.contains("rdf")) {
            return FeedFormat.RSS_1_0;
        }

        // 尝试通过子元素判断
        if (doc.getElementsByTagName("entry").getLength() > 0) {
            return FeedFormat.ATOM;
        }
        if (doc.getElementsByTagName("item").getLength() > 0) {
            return FeedFormat.RSS_2_0;
        }

        return FeedFormat.UNKNOWN;
    }

    /**
     * 根据格式获取条目节点列表
     */
    private static NodeList getItemNodes(Document doc, FeedFormat format) {
        return switch (format) {
            case ATOM -> doc.getElementsByTagName("entry");
            case RSS_1_0, RSS_2_0 -> doc.getElementsByTagName("item");
            case UNKNOWN -> {
                // 尝试两种方式
                NodeList items = doc.getElementsByTagName("item");
                if (items.getLength() == 0) {
                    items = doc.getElementsByTagName("entry");
                }
                yield items;
            }
        };
    }

    /**
     * 根据格式构建Article实体
     */
    private static ParsedItem buildParsedItem(Element item, FeedFormat format) {
        if (item == null) return null;

        return switch (format) {
            case ATOM -> buildFromAtomItem(item);
            case RSS_1_0, RSS_2_0, UNKNOWN -> buildFromRssItem(item);
        };
    }

    /**
     * 从RSS格式条目构建Article
     */
    private static ParsedItem buildFromRssItem(Element item) {
        // title
        String title = getElementText(item, "title");

        // link
        String link = getElementText(item, "link");

        // guid
        String guid = getElementText(item, "guid");

        // description
        String description = getElementText(item, "description");

        // content: content:encoded -> content -> description
        String content = getElementTextNS(item, "http://purl.org/rss/1.0/modules/content/", "encoded");
        if (content == null) content = getElementText(item, "content");
        if (content == null) {
            content = description;
            description = null;
        }

        // author: author -> dc:creator
        String author = getElementText(item, "author");
        if (author == null) {
            author = getElementTextNS(item, "http://purl.org/dc/elements/1.1/", "creator");
        }

        // pubDate: pubDate -> dc:date
        String pubDateStr = getElementText(item, "pubDate");
        if (pubDateStr == null) {
            pubDateStr = getElementTextNS(item, "http://purl.org/dc/elements/1.1/", "date");
        }

        // categories
        String categories = extractCategories(item);

        return new ParsedItem(title, link, guid, description, content, author, pubDateStr, categories);
    }

    /**
     * 从Atom格式条目构建Article
     */
    private static ParsedItem buildFromAtomItem(Element entry) {
        // title
        String title = getElementText(entry, "title");

        // link: 优先取rel="alternate"的href，否则取第一个link的href
        String link = extractAtomLink(entry);

        // id (作为guid)
        String guid = getElementText(entry, "id");

        // summary (作为description)
        String description = getElementText(entry, "summary");

        // content
        String content = getElementText(entry, "content");
        if (content == null) content = description;

        // author: author/name
        String author = extractAtomAuthor(entry);

        // published -> updated
        String pubDateStr = getElementText(entry, "published");
        if (pubDateStr == null) {
            pubDateStr = getElementText(entry, "updated");
        }

        // categories
        String categories = extractAtomCategories(entry);

        return new ParsedItem(title, link, guid, description, content, author, pubDateStr, categories);
    }

    /**
     * 将轻量条目转换为Article，包含HTML转Markdown等重处理
     */
    public static Article buildArticle(ParsedItem item, RssSource source) {
        if (item == null || source == null) return null;
        return buildFinalArticle(source, item.title(), item.link(), item.guid(),
                item.description(), item.rawContent(), item.author(), item.pubDateStr(), item.categories());
    }

    /**
     * 构建最终的Article实体，处理所有边界情况和默认值
     */
    private static Article buildFinalArticle(
            RssSource source,
            String title,
            String link,
            String guid,
            String description,
            String rawContent,
            String author,
            String pubDateStr,
            String categories
    ) {
        // 解析发布时间，失败则使用当前时间
        LocalDateTime pubDate = tryParsePubDate(pubDateStr);
        if (pubDate == null) {
            pubDate = LocalDateTime.now();
        }

        // title fallback
        if (isBlank(title)) {
            if (!isBlank(description)) {
                String cleaned = cleanHtml(description);
                title = cleaned.length() > 120 ? cleaned.substring(0, 120) + "..." : cleaned;
            } else if (!isBlank(link)) {
                title = link;
            } else {
                title = "(无标题)";
            }
        } else {
            title = cleanHtml(title);
        }

        // guid fallback: 优先link，否则生成唯一标识
        if (isBlank(guid)) {
            guid = !isBlank(link) ? link : "generated-" + System.nanoTime();
        }


        String markdownContent = null;
        if (rawContent != null && !rawContent.isBlank()) {
            long begin = System.currentTimeMillis();
            markdownContent = converter.convert(rawContent);
            log.debug("HTML转Markdown耗时: {} ms", System.currentTimeMillis() - begin);
        }

        // 如果转换后的内容为空，则认为此条目无效（可能是视频或格式不正确），不创建文章
        if (isBlank(markdownContent)) {
            log.debug("文章内容为空，已跳过: title={}, link={}", title, link);
            return null;
        }

        Long wordCount = countWords(markdownContent);
        // 如果字数少于100，直接跳过
        if (wordCount < 100) {
            log.debug("文章字数过少，已跳过: title={}, link={}, wordCount={}", title, link, wordCount);
            return null;
        }

        String coverImage = extractFirstImage(markdownContent);

        return Article.builder()
                .source(source)
                .sourceName(source.getName())
                .title(title.trim())
                .link(link != null ? link.trim() : null)
                .guid(guid.trim())
                .description(description)
                .rawContent(rawContent)
                .content(markdownContent)
                .wordCount(wordCount)
                .coverImage(coverImage)
                .author(author != null ? author.trim() : null)
                .pubDate(pubDate)
                .categories(categories)
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 提取Atom格式的link（优先rel="alternate"）
     */
    private static String extractAtomLink(Element entry) {
        NodeList linkNodes = entry.getElementsByTagName("link");
        String alternateLink = null;
        String firstLink = null;

        for (int i = 0; i < linkNodes.getLength(); i++) {
            Element linkEl = (Element) linkNodes.item(i);
            String href = linkEl.getAttribute("href");
            String rel = linkEl.getAttribute("rel");

            if (firstLink == null && !isBlank(href)) {
                firstLink = href;
            }
            if ("alternate".equals(rel) || isBlank(rel)) {
                alternateLink = href;
                break;
            }
        }

        return alternateLink != null ? alternateLink : firstLink;
    }

    /**
     * 提取Atom格式的author/name
     */
    private static String extractAtomAuthor(Element entry) {
        NodeList authorNodes = entry.getElementsByTagName("author");
        if (authorNodes.getLength() > 0) {
            Element authorEl = (Element) authorNodes.item(0);
            String name = getElementText(authorEl, "name");
            if (!isBlank(name)) return name;
        }
        return null;
    }

    /**
     * 提取Atom格式的categories（从term属性）
     */
    private static String extractAtomCategories(Element entry) {
        NodeList categoryNodes = entry.getElementsByTagName("category");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element catEl = (Element) categoryNodes.item(i);
            String term = catEl.getAttribute("term");
            if (!isBlank(term)) {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(term.trim());
            }
        }
        return sb.toString();
    }

    /**
     * 提取RSS格式的categories
     */
    private static String extractCategories(Element item) {
        NodeList categoryNodes = item.getElementsByTagName("category");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            String text = categoryNodes.item(i).getTextContent();
            if (!isBlank(text)) {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(text.trim());
            }
        }
        return sb.toString();
    }

    /**
     * 从元素中安全读取文本内容（按标签名）
     */
    public static String getElementText(Element parent, String tagName) {
        if (parent == null || tagName == null) return null;
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    /**
     * 从元素中读取带命名空间的标签文本
     */
    private static String getElementTextNS(Element parent, String namespaceURI, String localName) {
        if (parent == null) return null;
        NodeList nodeList = parent.getElementsByTagNameNS(namespaceURI, localName);
        if (nodeList.getLength() > 0) {
            String text = nodeList.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    /**
     * 尝试解析常见的RSS/Atom日期字符串
     */
    public static LocalDateTime tryParsePubDate(String dateStr) {
        if (isBlank(dateStr)) return null;

        String trimmed = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(trimmed, formatter);
                return zdt.toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试
            }
        }

        // 尝试LocalDateTime直接解析（无时区）
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        log.warn("无法解析日期: {}", dateStr);
        return null;
    }

    /**
     * 简单清理HTML标签，保留纯文本
     */
    public static String cleanHtml(String html) {
        if (html == null) return null;
        return html
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&apos;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    private static boolean isLikelyHeadingByStyleAndText(org.jsoup.nodes.Element element, String text) {
        if (text.length() > 36) {
            return false;
        }

        if (isLikelyMetaText(text)) {
            return false;
        }

        double maxFontSize = extractMaxFontSizePx(element);
        return maxFontSize >= 16;
    }

    private static boolean isLikelyMetaText(String text) {
        String normalized = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);

        if (META_SEPARATOR_PATTERN.matcher(text).find() && normalized.length() <= 40) {
            return true;
        }

        return normalized.contains("出品")
                || normalized.contains("工作室")
                || normalized.contains("责任编辑")
                || normalized.contains("编辑")
                || normalized.contains("记者")
                || normalized.contains("来源")
                || normalized.contains("作者")
                || normalized.contains("转载")
                || normalized.startsWith("文/")
                || normalized.startsWith("图/");
    }

    private static double extractMaxFontSizePx(org.jsoup.nodes.Element element) {
        double max = extractFontSizeFromStyle(element.attr("style"));

        for (org.jsoup.nodes.Element child : element.select("*[style*=font-size]")) {
            max = Math.max(max, extractFontSizeFromStyle(child.attr("style")));
        }

        return max;
    }

    private static double extractFontSizeFromStyle(String style) {
        if (isBlank(style)) {
            return 0;
        }

        Matcher matcher = FONT_SIZE_PATTERN.matcher(style);
        double max = 0;
        while (matcher.find()) {
            try {
                max = Math.max(max, Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // ignore invalid font-size
            }
        }

        return max;
    }

    /**
     * 统计文章字数（中文按一个字，英文按一个单词）
     */
    private static Long countWords(String content) {
        if (isBlank(content)) {
            return 0L;
        }

        long count = 0;
        boolean inWord = false; // 标记当前是否处于英文单词中

        for (char c : content.toCharArray()) {
            if (isChinese(c)) {
                count++;
                inWord = false; // 中文打断英文单词
            } else if (Character.isLetter(c)) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                // 数字、空格、标点符号等：不算字数，并重置单词状态
                inWord = false;
            }
        }

        return count;
    }

    /**
     * 判断字符是否为中文
     */
    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    /**
     * 从Markdown内容中提取第一个图片URL
     */
    private static String extractFirstImage(String content) {
        if (isBlank(content)) {
            return null;
        }

        // Markdown图片格式: ![alt](url)
        Pattern markdownImagePattern = Pattern.compile("!\\[.*?]\\((.*?)\\)");
        Matcher matcher = markdownImagePattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Feed格式枚举
     */
    public enum FeedFormat {
        RSS_2_0,    // RSS 2.0 (rss/channel/item)
        RSS_1_0,    // RSS 1.0 / RDF (rdf:RDF/item)
        ATOM,       // Atom (feed/entry)
        UNKNOWN
    }

    /**
     * 轻量解析结果
     */
    public record ParsedItem(
            String title,
            String link,
            String guid,
            String description,
            String rawContent,
            String author,
            String pubDateStr,
            String categories
    ) {
        public boolean hasIdentity() {
            return !isBlank(guid) || !isBlank(link);
        }
    }

    /**
     * Channel元信息记录类
     */
    public record ChannelInfo(String title, String description, String link) {
    }
}