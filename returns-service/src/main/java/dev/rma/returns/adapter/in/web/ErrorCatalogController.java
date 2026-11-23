package dev.rma.returns.adapter.in.web;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.rma.common.error.ApiExceptionHandler;

@RestController
public class ErrorCatalogController {
    private final String html;

    public ErrorCatalogController() throws IOException {
        var parser = Parser.builder().build();
        var renderer = HtmlRenderer.builder()
                .escapeHtml(true)
                .extensions(List.of(HeadingAnchorExtension.create()))
                .build();
        try (var source = new InputStreamReader(
                new ClassPathResource("docs/03-kontrakty.md").getInputStream(), StandardCharsets.UTF_8)) {
            html =
                    """
                    <!doctype html>
                    <html lang="pl">
                    <head><meta charset="utf-8"><title>Kontrakty i błędy API</title></head>
                    <body><main>
                    """
                            + renderer.render(parser.parseReader(source)) + "</main></body></html>";
        }
    }

    @GetMapping(path = ApiExceptionHandler.ERROR_CATALOG_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public String catalog() {
        return html;
    }
}
