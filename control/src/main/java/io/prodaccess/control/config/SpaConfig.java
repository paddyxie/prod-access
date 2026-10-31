package io.prodaccess.control.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the single-page UI. Client-side routes (/runs/123, /tasks/new, ...) have no file behind them, so any
 * extension-less path outside /api falls back to index.html and the browser router takes it from there.
 * Bundled assets have content hashes in their names and are cached for good; index.html is always revalidated,
 * so a deployment takes effect on the next page load.
 */
@Configuration
class SpaConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) return file;
                        boolean clientRoute = !path.startsWith("api/") && !path.substring(path.lastIndexOf('/') + 1).contains(".");
                        return clientRoute ? location.createRelative("index.html") : null;
                    }
                });
    }
}
