package com.jingwei.rsswithai.interfaces.front;

import com.jingwei.rsswithai.application.dto.ArticleDetailDTO;
import com.jingwei.rsswithai.application.dto.ArticleExtraDTO;
import com.jingwei.rsswithai.application.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

@Controller
@RequestMapping("/a")
@RequiredArgsConstructor
public class ShareRedirectController {

    private final ArticleService articleService;

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> shareRedirect(@PathVariable Long id) {
        ArticleDetailDTO article = articleService.getArticle(id, null);

        String overview = "";
        ArticleExtraDTO extra = articleService.getArticleExtra(id);
        if (extra != null && extra.overview() != null) {
            overview = extra.overview();
        }

        if (!overview.isBlank()) {
            overview = overview.replaceAll("<[^>]*>", "");
            // Remove markdown bold markers (**)
            overview = overview.replace("**", "");
            if (overview.length() > 200) {
                overview = overview.substring(0, 197) + "...";
            }
        }

        String title = article.title() != null ? HtmlUtils.htmlEscape(article.title()) : "RSSwithAI";
        String description = HtmlUtils.htmlEscape(overview);
        String imageUrl = article.coverImage() != null ? HtmlUtils.htmlEscape(article.coverImage()) : "";

        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>" + title + "</title>\n" +
                "    <meta property=\"og:title\" content=\"" + title + "\" />\n" +
                "    <meta property=\"og:description\" content=\"" + description + "\" />\n" +
                "    <meta property=\"og:image\" content=\"" + imageUrl + "\" />\n" +
                "    <meta property=\"og:type\" content=\"article\" />\n" +
                "    <script>\n" +
                "        window.location.href = '/discover?articleId=" + id + "';\n" +
                "    </script>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <p>正在跳转到文章详情...</p>\n" +
                "</body>\n" +
                "</html>";

        return ResponseEntity.ok(html);
    }
}
