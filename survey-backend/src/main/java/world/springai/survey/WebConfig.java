package world.springai.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS 設定：只允許設定檔列出的來源呼叫 /api/** */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;
    /** 從 app.cors-allowed-origins 讀逗號分隔的允許來源 */
    public WebConfig(@Value("${app.cors-allowed-origins}") String origins) {
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * 讓 /r/** 能取到 static/reader/ 下的資源（如 reader.css）。
     * HTML 頁面本身由 controller 提供（需要 server 端渲染），這裡只服務靜態附件。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/r/*.css", "/r/*.js")
                .addResourceLocations("classpath:/static/reader/");
    }
}
