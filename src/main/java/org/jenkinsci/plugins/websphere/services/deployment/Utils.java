/*
 * The MIT License
 *
 * Copyright 2016 User.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author User
 */
public final class Utils {

    private Utils() {
    }
    
    public static String getStackTraceFor(Exception e) {        
        StringWriter result = new StringWriter();
        PrintWriter out = new PrintWriter(result);
        e.printStackTrace(out);
        out.close();
        result.flush();
        return result.toString();
    }

    public static String targetsAsString(List<TargetDescription> targets) {
        final StringBuilder result = new StringBuilder("");

        for (TargetDescription d : targets) {
            final StringBuilder builder = new StringBuilder();
            builder.append("WebSphere:");
            boolean isNotEmptyTarget = false;
            isNotEmptyTarget |= appendTarget(builder, "cell=", d.getCell());
            isNotEmptyTarget |= appendTarget(builder, "cluster=", d.getCluster());
            isNotEmptyTarget |= appendTarget(builder, "node=", d.getNode());
            isNotEmptyTarget |= appendTarget(builder, "server=", d.getServer());
            if (isNotEmptyTarget) {
                builder.deleteCharAt(builder.length() - 1);
                result.append(builder.toString());
                result.append("+");
            }
        }
        if (result.length() > 0) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    private static boolean appendTarget(StringBuilder builder, String target, String value) {
        if (StringUtils.isNotEmpty(value)) {
            builder.append(target);
            builder.append(value);
            builder.append(",");
            return true;
        }
        return false;
    }

}
