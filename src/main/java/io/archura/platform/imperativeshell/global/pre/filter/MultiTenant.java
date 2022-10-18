package io.archura.platform.imperativeshell.global.pre.filter;

import io.archura.platform.api.context.Context;
import io.archura.platform.api.http.HttpServerRequest;
import io.archura.platform.api.logger.Logger;
import io.archura.platform.api.mapper.Mapper;
import io.archura.platform.api.type.Configurable;
import io.archura.platform.imperativeshell.global.pre.filter.exception.ConfigurationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;

public class MultiTenant implements Consumer<HttpServerRequest>, Configurable {

    public static final String ATTRIBUTE_TENANT_ID = "ARCHURA_REQUEST_TENANT_ID";
    public static final String DEFAULT_TENANT = "default";
    public static final String HOST_HEADER_NAME = "host";
    private Map<String, Object> configuration;
    private Logger logger;

    @Override
    public void accept(HttpServerRequest request) {
        final Map<String, Object> attributes = request.getAttributes();
        final Context context = (Context) attributes.get(Context.class.getSimpleName());
        logger = context.getLogger();
        logger.debug("configuration: %s", configuration);
        attributes.put(ATTRIBUTE_TENANT_ID, DEFAULT_TENANT);
        if (nonNull(configuration)) {
            final Mapper objectMapper = context.getMapper();
            final MultiTenantConfiguration config = getConfig(objectMapper);
            logger.debug("MultiTenantConfiguration config: %s", config);
            if (isHostConfigValid(config.getHost())) {
                logger.debug("Host configuration is valid.");
                final MultiTenantConfiguration.Host hostConfig = config.getHost();
                final String input = request.getFirstHeader(HOST_HEADER_NAME);
                final String regex = hostConfig.getRegex();
                final String groupName = hostConfig.getGroupName();
                handleTenant(attributes, regex, groupName, input);
            }
            if (isHeaderConfigValid(config.getHeader())) {
                logger.debug("Header configuration is valid.");
                final MultiTenantConfiguration.Header headerConfig = config.getHeader();
                final String input = request.getFirstHeader(headerConfig.getName());
                final String regex = headerConfig.getRegex();
                final String groupName = headerConfig.getGroupName();
                handleTenant(attributes, regex, groupName, input);
            }
            if (isPathConfigValid(config.getPath())) {
                logger.debug("Path configuration is valid.");
                final MultiTenantConfiguration.Path pathConfig = config.getPath();
                final String input = request.getRequestURI().getPath();
                final String regex = pathConfig.getRegex();
                final String groupName = pathConfig.getGroupName();
                handleTenant(attributes, regex, groupName, input);
            }
        }
        logger.debug("Filter set the tenant value to: %s", attributes.get(ATTRIBUTE_TENANT_ID));
    }

    private MultiTenantConfiguration getConfig(Mapper mapper) {
        try {
            String value = mapper.writeValueAsString(configuration);
            return mapper.readValue(value.getBytes(StandardCharsets.UTF_8), MultiTenantConfiguration.class);
        } catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }

    private boolean isPathConfigValid(MultiTenantConfiguration.Path path) {
        return nonNull(path) && nonNull(path.getRegex()) && nonNull(path.getGroupName()) && notEmpty(path.getRegex());
    }

    private boolean isHeaderConfigValid(MultiTenantConfiguration.Header header) {
        return nonNull(header) && nonNull(header.getName()) && nonNull(header.getRegex()) && nonNull(header.getGroupName())
                && notEmpty(header.getName()) && notEmpty(header.getRegex());
    }

    private boolean isHostConfigValid(MultiTenantConfiguration.Host host) {
        return nonNull(host) && nonNull(host.getGroupName()) && nonNull(host.getRegex()) && notEmpty(host.getRegex());
    }

    private void handleTenant(Map<String, Object> attributes, String regex, String groupName, String input) {
        if (nonNull(input)) {
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(input);
            boolean matchFound = matcher.find();
            if (matchFound) {
                if (notEmpty(groupName)) {
                    final String tenantName = matcher.group(groupName);
                    attributes.put(ATTRIBUTE_TENANT_ID, tenantName);
                } else {
                    final String tenantName = matcher.group(0);
                    attributes.put(ATTRIBUTE_TENANT_ID, tenantName);
                }
                logger.debug("Set tenant value to: %s", attributes.get(ATTRIBUTE_TENANT_ID));
            }
        }
    }

    private boolean notEmpty(String value) {
        return !value.trim().isEmpty();
    }

    @Override
    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
}
