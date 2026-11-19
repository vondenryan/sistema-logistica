package logistics.deliveries.infrastructure.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
  @Bean
  public Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);

              SimpleModule module = new SimpleModule();
              module.addSerializer(
                  Instant.class,
                  new JsonSerializer<Instant>() {
                    @Override
                    public void serialize(
                        Instant value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                      if (value == null) {
                        gen.writeNull();
                      } else {
                        gen.writeString(
                            new DateTimeFormatterBuilder()
                                .appendInstant(3)
                                .toFormatter()
                                .format(value));
                      }
                    }
                  });
              mapper.registerModule(module);
            });
  }
}
