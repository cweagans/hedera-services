/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.demo.platform.streamevent;

import static com.swirlds.logging.LogMarker.EVENT_PARSER;

import com.swirlds.common.system.events.Event;
import com.swirlds.platform.StreamEventParser;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * This is a utility file to read back evts file generated by stream server and check
 * whether event data can be deserialized properly
 */
public class LoadEventFile extends Thread {

    private static final Logger logger = LogManager.getLogger(LoadEventFile.class);
    private static final Marker MARKER = MarkerManager.getMarker("DEMO_INFO");

    static final File logPath = canonicalFile(".", "log4j2.xml");

    /** new marker for stream event start after which a version number is expected */
    static final byte STREAM_EVENT_START_WITH_VERSION = 0x5a;

    /**
     * new marker for stream event start with no transaction embedded in event,
     * after which a version number is expected
     */
    static final byte STREAM_EVENT_START_NO_TRANS_WITH_VERSION = 0x5b;

    static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

    /** keep track received sequence for each node ID during write to file */
    static HashMap<Long, Long> writeSeqMap = new HashMap<>();

    static final byte TYPE_PREV_HASH = 1; // next 48 bytes are hash384 of previous files
    static final byte TYPE_EVENT = 2; // next data type is event
    static final byte TYPE_SIGNATURE = 3; // next bytes are signature
    static final byte TYPE_FILE_HASH = 4; // next 48 bytes are hash384 of content of the file to be signed

    /**
     * Given a sequence of directory and file names, such as {".","sdk","test","..","config.txt"}, convert
     * it to a File in canonical form, such as /full/path/sdk/config.txt by assuming it starts in the
     * current working directory (which is the same as System.getProperty("user.dir")).
     *
     * @param names
     * 		the sequence of names
     * @return the File in canonical form
     */
    static File canonicalFile(String... names) {
        return canonicalFile(new File("."), names);
    }

    /**
     * Given a starting directory and a sequence of directory and file names, such as "sdk" and
     * {"data","test","..","config.txt"}, convert it to a File in canonical form, such as
     * /full/path/sdk/data/config.txt by assuming it starts in the current working directory (which is the
     * same as System.getProperty("user.dir")).
     *
     * @param names
     * 		the sequence of names
     * @return the File in canonical form, or null if there are any errors
     */
    static File canonicalFile(File start, String... names) {
        File f = start;
        try {
            f = f.getCanonicalFile();
            for (int i = 0; i < names.length; i++) {
                f = new File(f, names[i]).getCanonicalFile();
            }
        } catch (IOException e) {
            f = null;
        }
        return f;
    }

    static boolean eventHandler(Event event) {
        System.out.println(event);
        return true;
    }

    public static void main(String[] args) throws Exception {

        try {
            if (logPath.exists()) {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                context.setConfigLocation(logPath.toURI());
            }
        } catch (Exception e) {
            // should log this, but the log can't exist at this point.
            // e.printStackTrace();
        }

        // Load and parse a EventStream file
        String fileName = System.getProperty("file");
        if (fileName != null) {
            File fileOrDir = new File(fileName);
            if (fileOrDir.isDirectory()) {
                File[] files =
                        fileOrDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".evts"));
                logger.info(EVENT_PARSER.getMarker(), "Files before sorting {}", (Object[]) files);
                // sort file by its name and timestamp order
                Arrays.sort(files);
                for (File file : files) {
                    logger.info(MARKER, "Loading event file {} ", file);
                    StreamEventParser.parseEventStreamFile(file, LoadEventFile::eventHandler, true);
                }
            } else {
                logger.info(MARKER, "Loading event file {} ", fileName);
                StreamEventParser.parseEventStreamFile(fileOrDir, LoadEventFile::eventHandler, true);
            }
        }

        // Load and parse a signature file
        String sigFileName = System.getProperty("sig");
        if (sigFileName != null) {
            logger.info(MARKER, "Loading signature file {} ", sigFileName);
            Pair<byte[], byte[]> pair = extractHashAndSigFromFile(new File(sigFileName));
            logger.info(MARKER, "File Hash: {}; Signature: {}", () -> pair.getLeft(), () -> pair.getRight());
        }

        // Compare if EventStream files with the same name generated by different nodes have the same content
        String folderName = System.getProperty("folder");
        if (folderName != null) {
            logger.info(MARKER, "Validating folder {} ", folderName);
            validateEventStreamFiles(folderName);
        }
    }

    /**
     * 1. Extract the Hash of the content of corresponding EventStream file. This Hash is the signed Content of this
     * signature
     * 2. Extract signature from the file.
     *
     * @param file
     * @return
     */
    public static Pair<byte[], byte[]> extractHashAndSigFromFile(File file) {
        if (file.exists() == false) {
            logger.info(MARKER, "File does not exist " + file.getPath());
            return null;
        }

        try (final FileInputStream stream = new FileInputStream(file)) {
            DataInputStream dis = new DataInputStream(stream);
            byte[] fileHash = new byte[48];
            byte[] sig = null;
            while (dis.available() != 0) {
                try {
                    byte typeDelimiter = dis.readByte();

                    switch (typeDelimiter) {
                        case TYPE_FILE_HASH:
                            dis.readFully(fileHash);
                            break;

                        case TYPE_SIGNATURE:
                            int sigLength = dis.readInt();
                            byte[] sigBytes = new byte[sigLength];
                            dis.readFully(sigBytes);
                            sig = sigBytes;
                            break;
                        default:
                            logger.error(
                                    MARKER,
                                    "extractHashAndSigFromFile :: Exception Unknown record file delimiter {}",
                                    typeDelimiter);
                    }

                } catch (Exception e) {
                    logger.error(MARKER, "extractHashAndSigFromFile :: Exception ", e);
                    break;
                }
            }

            return Pair.of(fileHash, sig);
        } catch (FileNotFoundException e) {
            logger.error(MARKER, "extractHashAndSigFromFile :: File Not Found: ", e);
        } catch (IOException e) {
            logger.error(MARKER, "extractHashAndSigFromFile :: IOException: ", e);
        } catch (Exception e) {
            logger.error(MARKER, "extractHashAndSigFromFile :: Parsing Error: ", e);
        }
        return null;
    }

    private static void validateEventStreamFiles(String folderName) throws IOException {
        // fileName as key, a list of File as value
        HashMap<String, List<File>> map = new HashMap<>();
        File folder = new File(folderName);
        if (!folder.isDirectory() || !folder.exists()) {
            logger.error(MARKER, "{} is not a Directory or doesn't exist.", folderName);
            return;
        }

        for (File subFolder : folder.listFiles()) {
            if (!subFolder.isDirectory() || !subFolder.exists()) {
                logger.error(MARKER, "{} is not a Directory or doesn't exist.", subFolder);
                continue;
            }
            for (File file : subFolder.listFiles()) {
                if (!isEventStreamFile(file)) {
                    continue;
                }
                List<File> files = map.getOrDefault(file.getName(), new ArrayList<>());
                files.add(file);
                map.put(file.getName(), files);
            }
        }

        if (checkValueContentEquals(map)) {
            logger.info(MARKER, "EventStream files with the same name in {} have the same content. Valid.", folderName);
        }
    }

    /**
     * Check if for each key, its value - the list of Files have the same content
     */
    private static boolean checkValueContentEquals(Map<String, List<File>> map) throws IOException {
        for (String fileName : map.keySet()) {
            List<File> files = map.get(fileName);
            File file0 = files.get(0);
            for (int i = 1; i < files.size(); i++) {
                if (!FileUtils.contentEquals(file0, files.get(i))) {
                    logger.warn(
                            MARKER,
                            "Invalid! {} and {} have different content",
                            file0.getPath(),
                            files.get(i).getPath());
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isEventStreamFile(File file) {
        return file.getName().endsWith(".evts");
    }
}
