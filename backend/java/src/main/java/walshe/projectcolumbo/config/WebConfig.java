package walshe.projectcolumbo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import walshe.projectcolumbo.persistence.model.Timeframe;

@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Timeframe.class, Timeframe::fromValue);
    }
}
