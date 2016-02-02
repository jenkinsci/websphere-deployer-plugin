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

import hudson.Util;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author User
 */
public class ArtifactDescription implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String artifact;
    private final String bindUri;

    //Extended settings
    private final Integer startupOrder;
    private ClassLoadOrder classLoadOrder = ClassLoadOrder.DEFAULT;
    private WarClassLoaderPolicy warClassLoaderPolicy = WarClassLoaderPolicy.DEFAULT;
    private final Integer startingWeightWeb;
    private ClassLoadOrder classLoadOrderWeb = ClassLoadOrder.DEFAULT;

    @DataBoundConstructor
    public ArtifactDescription(String artifact, String bindUri,
            WarClassLoaderPolicy warClassLoaderPolicy,
            ClassLoadOrder classLoadOrder,
            ClassLoadOrder classLoadOrderWeb,
            Integer startupOrder,
            Integer startingWeightWeb) {
        this.artifact = Util.fixEmptyAndTrim(artifact);
        this.bindUri = Util.fixEmptyAndTrim(bindUri);
        this.warClassLoaderPolicy = warClassLoaderPolicy;
        this.classLoadOrder = classLoadOrder;
        this.classLoadOrderWeb = classLoadOrderWeb;
        this.startupOrder = startupOrder;
        this.startingWeightWeb = startingWeightWeb;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getBindUri() {
        return bindUri;
    }

    public WarClassLoaderPolicy getWarClassLoaderPolicy() {
        return warClassLoaderPolicy;
    }

    public boolean isDefaultWarClassLoaderPolicy() {
        return warClassLoaderPolicy == WarClassLoaderPolicy.DEFAULT;
    }

    public ClassLoadOrder getClassLoadOrder() {
        return classLoadOrder;
    }

    public boolean isDefaultClassLoadOrder() {
        return classLoadOrder == ClassLoadOrder.DEFAULT;
    }

    public ClassLoadOrder getClassLoadOrderWeb() {
        return classLoadOrderWeb;
    }

    public boolean isDefaultClassLoadOrderWeb() {
        return classLoadOrderWeb == ClassLoadOrder.DEFAULT;
    }

    public Integer getStartupOrder() {
        return startupOrder;
    }

    public Integer getStartingWeightWeb() {
        return startingWeightWeb;
    }
}
