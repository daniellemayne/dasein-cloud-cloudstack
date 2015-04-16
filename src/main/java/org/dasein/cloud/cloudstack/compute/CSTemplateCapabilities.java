/**
 * Copyright (C) 2009-2015 Dell, Inc.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.cloudstack.compute;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.cloudstack.CSCloud;
import org.dasein.cloud.cloudstack.CSException;
import org.dasein.cloud.cloudstack.CSMethod;
import org.dasein.cloud.cloudstack.Param;
import org.dasein.cloud.compute.ImageCapabilities;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageType;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.util.APITrace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes the capabilities of Cloudstack with respect to Dasein image operations.
 * User: daniellemayne
 * Date: 06/03/2014
 * Time: 08:31
 */
public class CSTemplateCapabilities extends AbstractCapabilities<CSCloud> implements ImageCapabilities{
    public CSTemplateCapabilities(@Nonnull CSCloud provider) {
        super(provider);
    }

    @Override
    public boolean canBundle(@Nonnull VmState fromState) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean canImage(@Nonnull VmState fromState) throws CloudException, InternalException {
        return fromState.equals(VmState.STOPPED);
    }

    @Nonnull
    @Override
    public String getProviderTermForImage(@Nonnull Locale locale, @Nonnull ImageClass cls) {
        switch( cls ) {
            case KERNEL: return "kernel template";
            case RAMDISK: return "ramdisk template";
        }
        return "template";
    }

    @Nonnull
    @Override
    public String getProviderTermForCustomImage(@Nonnull Locale locale, @Nonnull ImageClass cls) {
        return getProviderTermForImage(locale, cls);
    }

    @Nullable
    @Override
    public VisibleScope getImageVisibleScope() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Nonnull
    @Override
    public Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Nonnull
    @Override
    public Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException {
        ArrayList<MachineImageFormat> formats = new ArrayList<MachineImageFormat>();

        formats.add(MachineImageFormat.QCOW2);
        formats.add(MachineImageFormat.VHD);
        formats.add(MachineImageFormat.RAW);
        return formats;
    }

    @Nonnull
    @Override
    public Iterable<MachineImageFormat> listSupportedFormatsForBundling() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException {
        return Collections.singletonList(ImageClass.MACHINE);
    }

    @Nonnull
    @Override
    public Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException {
        return Collections.singletonList(MachineImageType.VOLUME);
    }

    @Override
    public boolean imageCaptureDestroysVM() throws InternalException, CloudException{
        return false;
    }

    @Override
    public boolean supportsDirectImageUpload() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageCapture(@Nonnull MachineImageType type) throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageCopy() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsImageSharing() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsImageSharingWithPublic() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "ImageCapabilities.supportsImageSharingWihtPublic");
        try {
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl("listCapabilities"), "listCapabilities");
            NodeList matches = doc.getElementsByTagName("userpublictemplateenabled");

            for( int i=0; i<matches.getLength(); i++ ) {
                Node node = matches.item(i);
                return Boolean.parseBoolean(node.getFirstChild().getNodeValue());
            }
            return false;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean supportsListingAllRegions() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsPublicLibrary(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return true;
    }
}
