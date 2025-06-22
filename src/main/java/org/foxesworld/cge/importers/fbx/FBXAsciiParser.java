package org.foxesworld.cge.importers.fbx;

import java.io.*;
import java.util.*;

public class FBXAsciiParser {
    public FBXNode parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        FBXNode root = new FBXNode("Root");
        Deque<FBXNode> stack = new ArrayDeque<>();
        stack.push(root);

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("{")) {
                String nodeName = line.substring(0, line.length() - 1).trim();
                FBXNode node = new FBXNode(nodeName);
                stack.peek().addChild(node);
                stack.push(node);
            } else if (line.equals("}")) {
                stack.pop();
            } else if (!line.isEmpty()) {
                stack.peek().addProperty(line);
            }
        }
        return root;
    }
}