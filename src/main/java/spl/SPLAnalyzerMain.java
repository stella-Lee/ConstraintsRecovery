package spl;

import java.util.List;
import java.util.Scanner;

public final class SPLAnalyzerMain {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: java spl.SPLAnalyzerMain [asset-subdirectory]");
            return;
        }

        AssetProjectPathResolver pathResolver = new AssetProjectPathResolver();
        System.out.println("Base asset directory: " + pathResolver.baseDirectoryDisplay());
        String assetSubdirectory = args.length == 1 ? args[0] : promptForAssetSubdirectory();
        AssetProjectPathResolver.ResolvedAssetProject assetProject;
        try {
            assetProject = pathResolver.resolve(assetSubdirectory);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }
        System.out.println("Analyzing asset project: " + assetProject.displayPath());

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extract(assetProject.normalizedPath());
        for (ConditionalBlock block : blocks) {
            printBlock(block);
        }
    }

    private static String promptForAssetSubdirectory() {
        System.out.print("Enter asset project subdirectory under assets: ");
        return new Scanner(System.in).nextLine();
    }

    private static void printBlock(ConditionalBlock block) {
        System.out.printf(
                "%s %s lines %d-%d depth %d products %s%n",
                block.filePath(),
                block.directiveType().token(),
                block.startLine(),
                block.endLine(),
                block.nestingDepth(),
                block.signature().productIds()
        );
        for (ConditionalBlock child : block.children()) {
            printBlock(child);
        }
    }
}
