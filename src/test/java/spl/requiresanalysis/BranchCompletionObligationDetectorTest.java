package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.ConditionalBlock;
import spl.ProductSignatureExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchCompletionObligationDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void createsOneDisjunctiveCandidateForNestedReturnProviders() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if HomeBasic | HomePremium | Ultimate
                        int base = 0;
                //#if HomeBasic | HomePremium
                        return 1;
                //#elif Ultimate
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(1, result.candidates().size());
        DisjunctiveImplementationEvidence candidate = result.candidates().get(0);
        assertEquals("DISJUNCTIVE_IMPLEMENTATION_EVIDENCE", candidate.candidateType());
        assertEquals("HomeBasic|HomePremium|Ultimate", compact(candidate.sourceSignature()));
        assertEquals(
                List.of("HomeBasic|HomePremium", "Ultimate"),
                candidate.providerSignatureSet().stream().map(this::compact).toList()
        );
        assertEquals("BRANCH_COMPLETION", candidate.evidenceType());
        assertEquals("NON_VOID_RETURN_COMPLETION", candidate.obligation());
        assertEquals(2, candidate.providerBlockIds().size());
    }

    @Test
    void doesNotSplitNestedReturnProvidersIntoPairwiseCandidates() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if P1 | P2
                //#if P1
                        return 1;
                //#elif P2
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(1, result.candidates().size());
        assertTrue(result.candidates().get(0).providerSignatureSet().containsAll(List.of("P1", "P2")));
    }

    @Test
    void skipsBranchWhenDirectBodyAlreadyReturns() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if P1 | P2
                        return 0;
                //#if P1
                        return 1;
                //#elif P2
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(0, result.candidates().size());
    }

    @Test
    void requiresProviderSignaturesToCoverSourceSignature() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if P1 | P2 | P3
                //#if P1
                        return 1;
                //#elif P2
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(0, result.candidates().size());
    }

    @Test
    void treatsFeatureIdeVariantMarkerAsJavaCodeForReturnDetection() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if P1 | P2
                //#if P1
                //@     return 1;
                //#elif P2
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(1, result.candidates().size());
        assertEquals(List.of("P1", "P2"), result.candidates().get(0).providerSignatureSet());
    }

    @Test
    void createsCandidateFromSiblingReturnBranchesUsingOnlyProductSignatures() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if HomeBasic | HomePremium
                        return 1;
                //#elif Ultimate
                        return 2;
                //#elif Enterprise | Professional
                        return compareDirectional();
                //#endif
                    }
                    int compareDirectional() {
                        return 0;
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(1, result.candidates().size());
        DisjunctiveImplementationEvidence candidate = result.candidates().get(0);
        assertEquals("SIBLING_BRANCH_COMPLETION", candidate.evidenceType());
        assertEquals("HomeBasic|HomePremium|Ultimate", compact(candidate.sourceSignature()));
        assertEquals(
                List.of("HomeBasic|HomePremium", "Ultimate"),
                candidate.providerSignatureSet().stream().map(this::compact).toList()
        );
        assertTrue(candidate.cautionReasons().contains("PRODUCT_ID_ONLY_NO_FEATURE_NAME_USED"));
    }

    @Test
    void doesNotDuplicateSiblingCandidateWhenExplicitOuterBranchExists() throws Exception {
        Path file = write("""
                class Example {
                    int compare() {
                //#if P1 | P2
                //#if P1
                        return 1;
                //#elif P2
                        return 2;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(1, result.candidates().size());
        assertEquals("BRANCH_COMPLETION", result.candidates().get(0).evidenceType());
    }

    @Test
    void ignoresVoidMethods() throws Exception {
        Path file = write("""
                class Example {
                    public void compare() {
                //#if P1 | P2
                //#if P1
                        return;
                //#elif P2
                        return;
                //#endif
                //#endif
                    }
                }
                """);

        DisjunctiveImplementationEvidenceResult result = detect(file);

        assertEquals(0, result.candidates().size());
    }

    private DisjunctiveImplementationEvidenceResult detect(Path file) throws Exception {
        List<ConditionalBlock> blocks = new ProductSignatureExtractor().extractFromFile(file);
        return new BranchCompletionObligationDetector().detectBranchCompletionObligations(blocks);
    }

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("Example.java");
        Files.writeString(file, content);
        return file;
    }

    private String compact(String signature) {
        return signature.replace(" ", "");
    }
}
