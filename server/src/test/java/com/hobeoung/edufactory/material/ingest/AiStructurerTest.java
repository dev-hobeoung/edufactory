package com.hobeoung.edufactory.material.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hobeoung.edufactory.common.Json;
import com.hobeoung.edufactory.material.ChunkInput;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiStructurerTest {

  @Test
  void toChunkKeepsOnlyKnownLabelsAndFixesTypes() {
    UUID known = UUID.randomUUID();
    String json = """
        {"chunkType":"QUESTION","title":null,"position":"3번","itemNo":"3","questionType":"BOGUS","content":"x+1=2",
         "choices":[{"label":"①","text":"1"},{"label":null,"text":"2"}],"answer":"①","explanation":null,"score":3,
         "tags":["방정식"," "],"labelIds":["%s","%s"],"pageFrom":2,"pageTo":null}
        """.formatted(known, UUID.randomUUID());
    ChunkInput c = AiStructurer.toChunk(Json.read(json), Set.of(known.toString()));
    assertEquals("QUESTION", c.chunkType());
    assertNull(c.questionType());
    assertEquals(List.of(known), c.labelIds());
    assertEquals(2, c.choices().size());
    assertEquals("2", c.choices().get(1).label());
    assertEquals(List.of("방정식"), c.tags());
    assertEquals(3.0, c.score());
    assertEquals(2, c.pageFrom());
    assertNull(c.pageTo());
  }

  @Test
  void answerKeyFillsMissingAnswers() {
    ChunkInput q = new ChunkInput("QUESTION", null, null, "2", "MULTIPLE_CHOICE", "q", List.of(), null, null, null,
        List.of(), List.of(), null, null);
    List<ChunkInput> out = AiStructurer.applyAnswerKey(List.of(q), Map.of("2", new String[] {"④", "해설"}));
    assertEquals("④", out.get(0).answer());
    assertEquals("해설", out.get(0).explanation());
  }

  @Test
  void splitKeepsAllText() {
    String text = ("문단 하나입니다.\n\n").repeat(5000);
    List<String> parts = AiStructurer.split(text, 30_000);
    assertTrue(parts.size() > 1);
    assertTrue(parts.stream().allMatch(p -> p.length() <= 30_100));
    assertEquals(text.replaceAll("\\s", "").length(), String.join("", parts).replaceAll("\\s", "").length());
  }

  @Test
  void schemaRequiresEveryProperty() {
    @SuppressWarnings("unchecked")
    Map<String, Object> chunk = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) AiStructurer
        .schema().get("properties")).get("chunks")).get("items");
    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) chunk.get("properties");
    assertEquals(props.keySet(), Set.copyOf((List<?>) chunk.get("required")));
  }
}
