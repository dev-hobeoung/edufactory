package com.hobeoung.edufactory.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentServiceTest {

  private static MessageParam msg(MessageParam.Role role, String text) {
    return MessageParam.builder().role(role)
        .contentOfBlockParams(List.of(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()))).build();
  }

  @Test
  void consecutiveAssistantMessagesAreMerged() {
    List<MessageParam> merged = AgentService.forRequest(List.of(msg(MessageParam.Role.USER, "u"),
        msg(MessageParam.Role.ASSISTANT, "a1"), msg(MessageParam.Role.ASSISTANT, "a2"),
        msg(MessageParam.Role.USER, "u2")));
    assertEquals(3, merged.size());
    assertEquals(2, merged.get(1).content().blockParams().orElseThrow().size());
  }

  @Test
  void userContentOrdersBlocks() {
    String content = AgentService.userContent("문제 만들어줘", "\n- 문항 수: 5\n", "<context_rules>x</context_rules>",
        null);
    assertTrue(content.startsWith("<conditions>"));
    assertTrue(content.indexOf("<context_rules>") < content.indexOf("<request>"));
    assertTrue(content.endsWith("</request>"));
  }
}
