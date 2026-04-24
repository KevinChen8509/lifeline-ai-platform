package com.project.subscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.entity.SubscriptionRule;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.repository.SubscriptionRuleRepository;
import com.project.subscription.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionEngine {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final SubscriptionRuleRepository ruleRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "sub:rules:";
    private static final long CACHE_TTL_MINUTES = 5;

    /**
     * 查找匹配指定设备（或设备类型）的活跃订阅 + 规则。
     * 先查 Redis 缓存，miss 时从 DB 加载并写入缓存。
     */
    public List<MatchedRule> findMatchingRules(Long deviceId, Long productId) {
        List<WebhookSubscription> subs = new ArrayList<>();

        // 设备级订阅
        subs.addAll(subscriptionRepository.findActiveByTargetAndType(deviceId, (short) 0));
        // 设备类型级订阅
        if (productId != null) {
            subs.addAll(subscriptionRepository.findActiveByTargetAndType(productId, (short) 1));
        }

        List<MatchedRule> results = new ArrayList<>();
        for (WebhookSubscription sub : subs) {
            List<SubscriptionRule> rules = getRulesWithCache(sub.getId());
            for (SubscriptionRule rule : rules) {
                if (!rule.getEnabled()) continue;
                results.add(new MatchedRule(sub, rule));
            }
        }
        return results;
    }

    /**
     * 评估阈值规则是否触发
     */
    public boolean evaluateThreshold(SubscriptionRule rule, double actualValue) {
        try {
            JsonNode cond = objectMapper.readTree(rule.getConditionJson());
            String operator = cond.get("operator").asText();
            double threshold = cond.get("threshold").asDouble();

            return switch (operator) {
                case "gt"  -> actualValue > threshold;
                case "gte" -> actualValue >= threshold;
                case "lt"  -> actualValue < threshold;
                case "lte" -> actualValue <= threshold;
                case "eq"  -> actualValue == threshold;
                case "neq" -> actualValue != threshold;
                default -> false;
            };
        } catch (Exception e) {
            log.error("规则评估失败 ruleId={}: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 检查冷却时间：同一规则在冷却期内不重复触发
     */
    public boolean isCooledDown(SubscriptionRule rule) {
        if (rule.getLastTriggeredAt() == null) return true;
        long cooldownMs = rule.getCooldownSeconds() * 1000L;
        return System.currentTimeMillis() - rule.getLastTriggeredAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 > cooldownMs;
    }

    /**
     * 更新规则最后触发时间
     */
    public void markTriggered(Long ruleId) {
        ruleRepository.findById(ruleId).ifPresent(rule -> {
            rule.setLastTriggeredAt(java.time.LocalDateTime.now());
            ruleRepository.save(rule);
            // 清除缓存
            redisTemplate.delete(CACHE_PREFIX + rule.getSubscriptionId());
        });
    }

    /**
     * 使订阅规则缓存失效（订阅变更时调用）
     */
    public void invalidateCache(Long subscriptionId) {
        redisTemplate.delete(CACHE_PREFIX + subscriptionId);
    }

    private List<SubscriptionRule> getRulesWithCache(Long subscriptionId) {
        String key = CACHE_PREFIX + subscriptionId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SubscriptionRule.class));
            } catch (JsonProcessingException e) {
                log.warn("缓存反序列化失败 subId={}, 从 DB 加载", subscriptionId);
            }
        }

        List<SubscriptionRule> rules = ruleRepository.findBySubscriptionId(subscriptionId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rules), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("缓存序列化失败 subId={}", subscriptionId);
        }
        return rules;
    }

    public record MatchedRule(WebhookSubscription subscription, SubscriptionRule rule) {}
}
