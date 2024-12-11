package com.syncduo.server;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

public class FileOperationTestUtil {

    private static final Random random = new Random();

    public static void createFolders(String path, int depth, int width) throws IOException {
        Path folderPath = Paths.get(path);
        createFiles(folderPath, 0, width);
        createFoldersRecursive(folderPath, depth, width, 1);
    }

    public static void deleteAllFoldersLeaveItSelf(Path path) throws IOException {
        if (!Files.exists(path)) {
            System.out.println("The specified path does not exist.");
            return;
        }

        // Use walkFileTree to recursively delete files and directories
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return super.visitFile(file, attrs);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.equals(path)) {
                    return super.postVisitDirectory(dir, exc);
                }
                Files.delete(dir);
                return super.postVisitDirectory(dir, exc);
            }
        });
        System.out.println("All folders and files have been deleted successfully.");
    }

    private static void createFoldersRecursive(
            Path path,
            int depth,
            int width,
            int currentDepth) throws IOException {
        // Base case: if depth exceeds, stop recursion
        if (currentDepth > depth) {
            return;
        }

        // Create directories for current depth
        for (int i = 1; i <= width; i++) {
            Path folderPath = path.resolve("TestFolder" + currentDepth + "_" + i);
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            // Create .txt and .bin files in the folder with specific naming
            createFiles(folderPath, currentDepth, i);

            // Recursively create subfolders for the next depth level
            createFoldersRecursive(folderPath, depth, width, currentDepth + 1);
        }
    }

    private static void createFiles(
            Path folderPath,
            int currentDepth,
            int folderNumber) throws IOException {
        // Create .txt file with the naming convention
        Path txtFile = folderPath.resolve("TestFolder" + currentDepth + "_" + folderNumber + ".txt");
        if (!Files.exists(txtFile)) {
            Files.createFile(txtFile);
        }

        // Write numbers 1 to 10 to the .txt file
        Files.write(txtFile, generateNumbers(), StandardOpenOption.WRITE);

        // Create .bin file with the naming convention
        Path binFile = folderPath.resolve("TestFolder" + currentDepth + "_" + folderNumber + ".bin");
        if (!Files.exists(binFile)) {
            Files.createFile(binFile);
        }

        // Write random binary data to the .bin file
        writeRandomBinaryData(binFile);
    }

    // pair<Txt File, Binary File>
    public static Pair<Path, Path> createTxtAndBinFile(
            Path folderPath) throws IOException {
        // Create .txt file with the naming convention
        Path txtFile = folderPath.resolve("1" + ".txt");
        if (!Files.exists(txtFile)) {
            Files.createFile(txtFile);
        }

        // Write numbers 1 to 10 to the .txt file
        Files.write(txtFile, generateNumbers(), StandardOpenOption.WRITE);

        // Create .bin file with the naming convention
        Path binFile = folderPath.resolve("1" + ".bin");
        if (!Files.exists(binFile)) {
            Files.createFile(binFile);
        }

        // Write random binary data to the .bin file
        writeRandomBinaryData(binFile);

        return new ImmutablePair<>(txtFile, binFile);
    }

    private static byte[] generateNumbers() {
        StringBuilder sb = new StringBuilder();

        // Generate 10 random numbers between 1 and 10000
        for (int i = 0; i < 10; i++) {
            int randomNumber = random.nextInt(10000) + 1; // Generates a random number from 1 to 10000
            sb.append(randomNumber).append(System.lineSeparator());
        }

        // Return the generated numbers as a byte array
        return sb.toString().getBytes();
    }

    private static void writeRandomBinaryData(Path binFile) throws IOException {
        byte[] randomBytes = new byte[1024]; // Generate 1 KB of random data
        random.nextBytes(randomBytes);
        Files.write(binFile, randomBytes, StandardOpenOption.WRITE);
    }
}
