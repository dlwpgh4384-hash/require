package com.company.common.service;

import com.company.common.dto.MenuResponse;
import com.company.common.dto.MenuResponse.ScreenAccess;
import com.company.common.mapper.MenuRow;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static com.company.common.service.MenuFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ExtendWith(OutputCaptureExtension.class)
class MenuRulesTest {
    @SuppressWarnings("unchecked")
    private List<ScreenAccess> access(MenuResponse response) {
        return (List<ScreenAccess>) response.meta().get("screenAccess");
    }

    @Test
    void disabledAncestorHidesItsEntireBranchWithoutMutatingCandidates() {
        var parent = row("parent", null, null, 1);
        parent.setUseYn("N");
        var child = row("child", "parent", "CHILD", 2);
        var grandchild = row("grandchild", "child", "GRAND", 3);
        var rows = List.of(parent, child, grandchild);
        var response = MenuRules.classify(rows);
        assertThat(response.data()).isEmpty();
        assertThat(access(response)).allMatch(item -> !item.available());
        assertThat(rows).hasSize(3);
        assertThat(child.getUpperMenuId()).isEqualTo("parent");
        assertThat(child.getUseYn()).isEqualTo("Y");
    }

    @Test
    void missingParentAndDescendantAreUnavailableAndWarn(CapturedOutput output) {
        var response = MenuRules.classify(List.of(row("orphan", "absent", "ORPHAN", 1), row("child", "orphan", "CHILD", 2)));
        assertThat(response.data()).isEmpty();
        assertThat(access(response)).allMatch(item -> !item.available());
        assertThat(output.getOut()).contains("WARN", "missing-parent", "orphan");
    }

    @Test
    void selfReferenceAndDescendantAreUnavailableAndWarn(CapturedOutput output) {
        var response = MenuRules.classify(List.of(row("self", "self", "SELF", 1), row("child", "self", "CHILD", 2)));
        assertThat(response.data()).isEmpty();
        assertThat(access(response)).allMatch(item -> !item.available());
        assertThat(output.getOut()).contains("WARN", "self-reference", "self");
    }

    @Test
    void multipleCyclesAndDescendantsTerminateAndWarn(CapturedOutput output) {
        var rows = List.of(row("a", "b", "A", 1), row("b", "a", "B", 2), row("c", "a", "C", 3),
            row("d", "e", "D", 4), row("e", "f", "E", 5), row("f", "d", "F", 6), row("g", "f", "G", 7));
        var response = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> MenuRules.classify(rows));
        assertThat(response.data()).isEmpty();
        assertThat(access(response)).hasSize(7).allMatch(item -> !item.available());
        assertThat(output.getOut()).contains("WARN", "cycle", "menuId=a", "menuId=d");
        assertThat(rows.get(0).getUpperMenuId()).isEqualTo("b");
    }

    @Test
    void nullAndEmptyParentAreRootsRegardlessOfLevel(CapturedOutput output) {
        var response = MenuRules.classify(List.of(row("null-root", null, "NULL", 2), row("empty-root", "", "EMPTY", 1)));
        assertThat(response.data()).extracting(item -> item.menuId()).containsExactly("empty-root", "null-root");
        assertThat(access(response)).allMatch(ScreenAccess::available);
        assertThat(output.getOut()).doesNotContain("Menu hierarchy");
    }

    @Test
    void disabledFirstDuplicateIsNeverPromotedAndOriginalRowsRemain(CapturedOutput output) {
        var first = row("a", null, "SAME", 1);
        first.setUseYn("N");
        var second = row("b", null, "SAME", 2);
        var rows = List.of(second, first);
        var response = MenuRules.classify(rows);
        assertThat(response.data()).isEmpty();
        assertThat(access(response)).containsExactly(new ScreenAccess("SAME", false, "N"));
        assertThat(rows).hasSize(2);
        assertThat(second.getUseYn()).isEqualTo("Y");
        assertThat(output.getOut()).contains("WARN", "duplicate", "representative=a", "suppressed=b");
    }

    @Test
    void pcOnlyFirstDuplicateRemainsRepresentativeWithMenuIdTieBreak() {
        var first = row("a", null, "SAME", 1);
        first.setPcOnlyYn("Y");
        var response = MenuRules.classify(List.of(row("b", null, "SAME", 1), first));
        assertThat(response.data()).extracting(item -> item.menuId()).containsExactly("a");
        assertThat(access(response)).containsExactly(new ScreenAccess("SAME", true, "Y"));
    }

    @Test
    void suppressedDuplicateParentDoesNotReparentOrExposeDescendants() {
        var response = MenuRules.classify(List.of(row("first", null, "SAME", 1), row("later", null, "SAME", 2),
            row("child", "later", "CHILD", 3), row("grand", "child", "GRAND", 4)));
        assertThat(response.data()).extracting(item -> item.menuId()).containsExactly("first");
        assertThat(access(response)).contains(new ScreenAccess("CHILD", false, "N"), new ScreenAccess("GRAND", false, "N"));
    }

    @Test
    void mixedGroupRemainsInTreeButItsScreenIsUnavailable(CapturedOutput output) {
        var response = MenuRules.classify(List.of(row("mixed", null, "MIXED", 1), row("child", "mixed", "CHILD", 2)));
        assertThat(response.data()).extracting(item -> item.menuId()).containsExactly("mixed", "child");
        assertThat(access(response)).containsExactly(new ScreenAccess("MIXED", false, "N"), new ScreenAccess("CHILD", true, "N"));
        assertThat(output.getOut()).contains("WARN", "mixed group", "menuId=mixed");
    }

    @Test
    void originalRelationshipsStillMakeAMixedGroupWhenChildIsDisabled(CapturedOutput output) {
        var child = row("child", "mixed", "CHILD", 2);
        child.setUseYn("N");
        var response = MenuRules.classify(List.of(row("mixed", null, "MIXED", 1), child));
        assertThat(access(response)).contains(new ScreenAccess("MIXED", false, "N"));
        assertThat(output.getOut()).contains("mixed group");
    }

    @Test
    void normalGroupAndUniqueScreensDoNotEmitMixedOrDuplicateWarnings(CapturedOutput output) {
        MenuRules.classify(List.of(row("group", null, null, 1), row("child", "group", "CHILD", 2)));
        assertThat(output.getOut()).doesNotContain("mixed group", "Menu duplicate", "Menu hierarchy");
    }

    @Test
    void emptyScreenIdsDoNotCreateAccessMetadataOrDuplicateWarnings(CapturedOutput output) {
        var response = MenuRules.classify(List.of(row("a", null, "", 1), row("b", null, "", 2)));
        assertThat(access(response)).isEmpty();
        assertThat(response.data()).hasSize(2);
        assertThat(output.getOut()).doesNotContain("Menu duplicate");
    }
}
