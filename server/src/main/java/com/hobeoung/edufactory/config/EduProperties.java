package com.hobeoung.edufactory.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 edufactory.* — 설정이 빠진 환경(테스트 등)에서도 기동되도록 모두 기본값을 둔다. */
@ConfigurationProperties(prefix = "edufactory")
public record EduProperties(Admin admin, Storage storage, Cors cors, Ai ai, Embedding embedding) {

  public static final String ZONE = "Asia/Seoul";

  public EduProperties {
    admin = admin == null ? new Admin(null, null) : admin;
    storage = storage == null ? new Storage(null) : storage;
    cors = cors == null ? new Cors(null) : cors;
    ai = ai == null ? new Ai(null, null, null, null, 0, 0, 0, true, true, 0) : ai;
    embedding = embedding == null ? new Embedding(null, null, null, 0) : embedding;
  }

  public record Admin(String email, String password) {
    public Admin {
      email = blank(email) ? "admin@edufactory.local" : email;
      password = blank(password) ? "admin1234" : password;
    }
  }

  public record Storage(String dir) {
    public Storage {
      dir = blank(dir) ? "./data/uploads" : dir;
    }
  }

  public record Cors(List<String> origins) {
    public Cors {
      origins = origins == null ? List.of("http://localhost:3010") : origins;
    }
  }

  /**
   * @param webSearchEnabled 웹 검색 서버 도구(창의성 단계의 외부 자료) 사용 여부
   */
  public record Ai(String apiKey, String model, String structureModel, String effort, long maxTokens,
      int maxToolCalls, double monthlyBudgetUsd, boolean fallbackEnabled, boolean webSearchEnabled,
      int webSearchMaxUses) {
    public Ai {
      model = blank(model) ? "claude-opus-5-5" : model;
      structureModel = blank(structureModel) ? "claude-opus-5-5" : structureModel;
      effort = blank(effort) ? "medium" : effort;
      maxTokens = maxTokens <= 0 ? 64000 : maxTokens;
      maxToolCalls = maxToolCalls <= 0 ? 24 : maxToolCalls;
      monthlyBudgetUsd = monthlyBudgetUsd <= 0 ? 100 : monthlyBudgetUsd;
      webSearchMaxUses = webSearchMaxUses <= 0 ? 8 : webSearchMaxUses;
    }
  }

  public record Embedding(String voyageApiKey, String voyageBaseUrl, String model, int dimension) {
    public Embedding {
      voyageBaseUrl = blank(voyageBaseUrl) ? "https://api.voyageai.com" : voyageBaseUrl;
      model = blank(model) ? "voyage-3.5" : model;
      dimension = dimension <= 0 ? 1024 : dimension;
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
