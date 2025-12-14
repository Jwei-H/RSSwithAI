package com.jingwei.rsswithai;

import com.jingwei.rsswithai.domain.model.Article;
import com.jingwei.rsswithai.domain.repository.ArticleRepository;
import com.jingwei.rsswithai.domain.repository.RssSourceRepository;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MyStartupRunner implements CommandLineRunner {

//    private static final FlexmarkHtmlConverter HTML_TO_MD_CONVERTER;
//    private static final Parser MD_PARSER;
//    private static final HtmlRenderer MD_TO_HTML_RENDERER;
//
//    static {
//        // 用于 HTML -> Markdown
//        HTML_TO_MD_CONVERTER = FlexmarkHtmlConverter.builder().build();
//        // 用于 Markdown -> HTML
//        MutableDataSet mdToHtmlOptions = new MutableDataSet();
//        MD_PARSER = Parser.builder(mdToHtmlOptions).build();
//        MD_TO_HTML_RENDERER = HtmlRenderer.builder(mdToHtmlOptions).build();
//    }
//
//    private final ArticleRepository articleRepository;
//    private final RssSourceRepository rssSourceRepository;

    @Override
    public void run(String @NotNull ... args) {
//        List<RssSource> rssSources = rssSourceRepository.findAll();
//        Article article = articleRepository.findById(680L).orElseThrow();
//        System.out.println(article.getContent());
//        List<Article> articles = articleRepository.findAll();
//        for (Article article : articles) {
//            // 1. 先将输入（可能是Markdown或HTML）统一转换为HTML
//            Node document = MD_PARSER.parse(article.getRawContent());
//            String unifiedHtml = MD_TO_HTML_RENDERER.render(document);
//
//            // 2. 再将统一后的HTML转换为Markdown
//            String markdownContent = HTML_TO_MD_CONVERTER.convert(unifiedHtml).trim();
//            //将 "\*\*" 转为 "**"
//            markdownContent = markdownContent.replace("\\*\\*", "**");
//            article.setContent(markdownContent);
//            articleRepository.save(article);
//        }
    }
}
