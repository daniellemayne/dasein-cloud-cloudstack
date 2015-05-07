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

package org.dasein.cloud.cloudstack.network;

import org.dasein.cloud.AbstractCapabilities;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.VisibleScope;
import org.dasein.cloud.cloudstack.CSCloud;
import org.dasein.cloud.network.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Describes the capabilities of Cloudstack with respect to Dasein security group operations.
 * <p>Created by Danielle Mayne: 3/04/14 10:11 AM</p>
 * @author Danielle Mayne
 * @version 2014.03 initial version
 * @since 2014.03
 */
public class SecurityGroupCapabilities extends AbstractCapabilities<CSCloud> implements FirewallCapabilities {

    public SecurityGroupCapabilities(CSCloud cloud) {super(cloud);}

    @Override
    public @Nonnull FirewallConstraints getFirewallConstraintsForCloud() throws InternalException, CloudException {
        return FirewallConstraints.getInstance();
    }

    @Override
    public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
        return "security group";
    }

    @Override
    public @Nullable VisibleScope getFirewallVisibleScope() {
        return null;
    }

    @Override
    public @Nonnull Requirement identifyPrecedenceRequirement(boolean inVlan) throws InternalException, CloudException {
        return Requirement.NONE;
    }

    @Override
    public boolean isZeroPrecedenceHighest() throws InternalException, CloudException {
        return true;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan) throws InternalException, CloudException {
        return listSupportedDestinationTypes(inVlan, Direction.INGRESS);
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<Direction> listSupportedDirections(boolean inVlan) throws InternalException, CloudException {
        if( inVlan ) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(Direction.INGRESS, Direction.EGRESS));
    }

    @Override
    public @Nonnull Iterable<Permission> listSupportedPermissions(boolean inVlan) throws InternalException, CloudException {
        if( inVlan ) {
            return Collections.emptyList();
        }
        return Collections.singletonList(Permission.ALLOW);
    }

    @Override
    public @Nonnull Iterable<Protocol> listSupportedProtocols( boolean inVlan ) throws InternalException, CloudException {
        return Collections.unmodifiableList(Arrays.asList(Protocol.TCP, Protocol.UDP));
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan) throws InternalException, CloudException {
        return listSupportedSourceTypes(inVlan, Direction.INGRESS);
    }

    @Override
    public boolean requiresRulesOnCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Requirement requiresVLAN() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public boolean supportsRules(@Nonnull Direction direction, @Nonnull Permission permission, boolean inVlan) throws CloudException, InternalException {
        return (!inVlan && permission.equals(Permission.ALLOW));
    }

    @Override
    public boolean supportsFirewallCreation(boolean inVlan) throws CloudException, InternalException {
        return !inVlan;
    }

    @Override
    public boolean supportsFirewallDeletion() throws CloudException, InternalException {
        return true;
    }

    @Override
    public Iterable<RuleTargetType> listSupportedDestinationTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        if( inVlan ) {
            return Collections.emptyList();
        }
        List<RuleTargetType> supportedDestinationTypes = new ArrayList<RuleTargetType>();
        if (direction.equals(Direction.INGRESS)) {
            supportedDestinationTypes = Collections.singletonList(RuleTargetType.GLOBAL);
        }
        else if (direction.equals(Direction.EGRESS)){
            supportedDestinationTypes = Collections.singletonList(RuleTargetType.CIDR);
        }
        return supportedDestinationTypes;
    }

    @Override
    public Iterable<RuleTargetType> listSupportedSourceTypes(boolean inVlan, Direction direction) throws InternalException, CloudException {
        if( inVlan ) {
            return Collections.emptyList();
        }
        List<RuleTargetType> supportedSourceTypes = new ArrayList<RuleTargetType>();
        if (direction.equals(Direction.INGRESS)) {
            supportedSourceTypes = Collections.singletonList(RuleTargetType.CIDR);
        }
        else if (direction.equals(Direction.EGRESS)){
            supportedSourceTypes = Collections.singletonList(RuleTargetType.GLOBAL);
        }
        return supportedSourceTypes;
    }
}
