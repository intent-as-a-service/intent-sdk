package dev.intent.sdk.host.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 宿主接入配置：{@code intent.host.*}。
 *
 * <p>默认值刻意选成"零建表、零依赖"——宿主引一个依赖就能跑起来看到效果，
 * 想接 DB / 后台管理再逐项打开。</p>
 */
@ConfigurationProperties(prefix = "intent.host")
public class IntentHostProperties {

    /** 意图与配置的存储方式。 */
    public enum Storage {
        /** 种子 YAML 载入内存，重启还原（默认，零建表）。 */
        MEMORY,
        /** 以磁盘目录为准，规格即文件（零建表，可持久化）。 */
        FILE
    }

    private Storage storage = Storage.MEMORY;
    private String specDir = "intent/specs";
    private String configDir = "intent/config";
    private String ruleDir = "intent/rules";
    private String traceDir = "";
    private String timeZone = "";
    private boolean rulesEnabled = true;
    private boolean validateRules = true;

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage == null ? Storage.MEMORY : storage;
    }

    public String getSpecDir() {
        return specDir;
    }

    public void setSpecDir(String specDir) {
        this.specDir = specDir;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getRuleDir() {
        return ruleDir;
    }

    public void setRuleDir(String ruleDir) {
        this.ruleDir = ruleDir;
    }

    public String getTraceDir() {
        return traceDir;
    }

    public void setTraceDir(String traceDir) {
        this.traceDir = traceDir;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public boolean isRulesEnabled() {
        return rulesEnabled;
    }

    public void setRulesEnabled(boolean rulesEnabled) {
        this.rulesEnabled = rulesEnabled;
    }

    public boolean isValidateRules() {
        return validateRules;
    }

    public void setValidateRules(boolean validateRules) {
        this.validateRules = validateRules;
    }
}