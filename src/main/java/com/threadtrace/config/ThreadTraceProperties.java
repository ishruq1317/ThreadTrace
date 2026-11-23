package com.threadtrace.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "threadtrace")
public class ThreadTraceProperties {

    @Valid
    @NotNull
    private MassBalanceProperties massBalance = new MassBalanceProperties();

    @Valid
    @NotNull
    private EvidenceProperties evidence = new EvidenceProperties();

    @Valid
    @NotNull
    private FootprintProperties footprint = new FootprintProperties();

    @Valid
    @NotNull
    private StorageProperties storage = new StorageProperties();

    @Valid
    @NotNull
    private PassportProperties passport = new PassportProperties();

    @NotEmpty
    private Map<String, @Valid ProcessRuleProperties> processes = new HashMap<>();

    public MassBalanceProperties getMassBalance() {
        return massBalance;
    }

    public void setMassBalance(MassBalanceProperties massBalance) {
        this.massBalance = massBalance;
    }

    public EvidenceProperties getEvidence() {
        return evidence;
    }

    public void setEvidence(EvidenceProperties evidence) {
        this.evidence = evidence;
    }

    public FootprintProperties getFootprint() {
        return footprint;
    }

    public void setFootprint(FootprintProperties footprint) {
        this.footprint = footprint;
    }

    public StorageProperties getStorage() {
        return storage;
    }

    public void setStorage(StorageProperties storage) {
        this.storage = storage;
    }

    public PassportProperties getPassport() {
        return passport;
    }

    public void setPassport(PassportProperties passport) {
        this.passport = passport;
    }

    public Map<String, ProcessRuleProperties> getProcesses() {
        return processes;
    }

    public void setProcesses(Map<String, ProcessRuleProperties> processes) {
        this.processes = processes;
    }

    public ProcessRuleProperties resolveProcessRule(String processStep) {
        if (processStep == null) {
            return defaultRule();
        }
        ProcessRuleProperties rule = processes.get(processStep.toLowerCase());
        return rule != null ? rule : defaultRule();
    }

    private ProcessRuleProperties defaultRule() {
        ProcessRuleProperties fallback = new ProcessRuleProperties();
        fallback.setAllowedLossPct(massBalance.getDefaultAllowedLossPct());
        fallback.setWaterLitersPerUnit(BigDecimal.ZERO);
        fallback.setEnergyKwhPerUnit(BigDecimal.ZERO);
        fallback.setCo2eKgPerUnit(BigDecimal.ZERO);
        return fallback;
    }

    public enum EnforcementMode {
        BLOCK, FLAG
    }

    public enum AllocationMethod {
        BY_MASS, BY_PIECE_COUNT
    }

    public static class MassBalanceProperties {
        @NotNull
        private EnforcementMode enforcementMode = EnforcementMode.FLAG;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private BigDecimal defaultAllowedLossPct = BigDecimal.valueOf(10.0);

        public EnforcementMode getEnforcementMode() {
            return enforcementMode;
        }

        public void setEnforcementMode(EnforcementMode enforcementMode) {
            this.enforcementMode = enforcementMode;
        }

        public BigDecimal getDefaultAllowedLossPct() {
            return defaultAllowedLossPct;
        }

        public void setDefaultAllowedLossPct(BigDecimal defaultAllowedLossPct) {
            this.defaultAllowedLossPct = defaultAllowedLossPct;
        }
    }

    public static class EvidenceProperties {
        @Min(1)
        private int expiryWarningDays = 30;

        @NotEmpty
        private List<@NotBlank String> allowedCertificateTypes = List.of("GOTS", "GRS", "OEKO_TEX_100");

        public int getExpiryWarningDays() {
            return expiryWarningDays;
        }

        public void setExpiryWarningDays(int expiryWarningDays) {
            this.expiryWarningDays = expiryWarningDays;
        }

        public List<String> getAllowedCertificateTypes() {
            return allowedCertificateTypes;
        }

        public void setAllowedCertificateTypes(List<String> allowedCertificateTypes) {
            this.allowedCertificateTypes = allowedCertificateTypes;
        }
    }

    public static class FootprintProperties {
        @NotNull
        private AllocationMethod defaultAllocationMethod = AllocationMethod.BY_MASS;

        public AllocationMethod getDefaultAllocationMethod() {
            return defaultAllocationMethod;
        }

        public void setDefaultAllocationMethod(AllocationMethod defaultAllocationMethod) {
            this.defaultAllocationMethod = defaultAllocationMethod;
        }
    }

    public static class StorageProperties {
        @NotBlank
        private String type = "local";

        @NotBlank
        private String localDir = "./storage/evidence";

        private String minioEndpoint = "http://localhost:9000";
        private String minioBucket = "threadtrace-evidence";
        private String minioAccessKey = "";
        private String minioSecretKey = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public String getMinioEndpoint() {
            return minioEndpoint;
        }

        public void setMinioEndpoint(String minioEndpoint) {
            this.minioEndpoint = minioEndpoint;
        }

        public String getMinioBucket() {
            return minioBucket;
        }

        public void setMinioBucket(String minioBucket) {
            this.minioBucket = minioBucket;
        }

        public String getMinioAccessKey() {
            return minioAccessKey;
        }

        public void setMinioAccessKey(String minioAccessKey) {
            this.minioAccessKey = minioAccessKey;
        }

        public String getMinioSecretKey() {
            return minioSecretKey;
        }

        public void setMinioSecretKey(String minioSecretKey) {
            this.minioSecretKey = minioSecretKey;
        }
    }

    public static class PassportProperties {
        @NotBlank
        private String publicBaseUrl = "https://threadtrace.onrender.com";

        @Min(1)
        private int cacheTtlSeconds = 60;

        @Min(0)
        private int staleWhileRevalidateSeconds = 300;

        private Map<String, String> extraFields = new HashMap<>();

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public int getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(int cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public int getStaleWhileRevalidateSeconds() {
            return staleWhileRevalidateSeconds;
        }

        public void setStaleWhileRevalidateSeconds(int staleWhileRevalidateSeconds) {
            this.staleWhileRevalidateSeconds = staleWhileRevalidateSeconds;
        }

        public Map<String, String> getExtraFields() {
            return extraFields;
        }

        public void setExtraFields(Map<String, String> extraFields) {
            this.extraFields = extraFields;
        }
    }

    public static class ProcessRuleProperties {
        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        private BigDecimal allowedLossPct = BigDecimal.ZERO;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal waterLitersPerUnit = BigDecimal.ZERO;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal energyKwhPerUnit = BigDecimal.ZERO;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal co2eKgPerUnit = BigDecimal.ZERO;

        public BigDecimal getAllowedLossPct() {
            return allowedLossPct;
        }

        public void setAllowedLossPct(BigDecimal allowedLossPct) {
            this.allowedLossPct = allowedLossPct;
        }

        public BigDecimal getWaterLitersPerUnit() {
            return waterLitersPerUnit;
        }

        public void setWaterLitersPerUnit(BigDecimal waterLitersPerUnit) {
            this.waterLitersPerUnit = waterLitersPerUnit;
        }

        public BigDecimal getEnergyKwhPerUnit() {
            return energyKwhPerUnit;
        }

        public void setEnergyKwhPerUnit(BigDecimal energyKwhPerUnit) {
            this.energyKwhPerUnit = energyKwhPerUnit;
        }

        public BigDecimal getCo2eKgPerUnit() {
            return co2eKgPerUnit;
        }

        public void setCo2eKgPerUnit(BigDecimal co2eKgPerUnit) {
            this.co2eKgPerUnit = co2eKgPerUnit;
        }
    }
}
