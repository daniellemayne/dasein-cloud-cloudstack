/**
 * Copyright (C) 2009-2014 Dell, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.cloudstack.CSCloud;
import org.dasein.cloud.cloudstack.CSException;
import org.dasein.cloud.cloudstack.CSMethod;
import org.dasein.cloud.cloudstack.Param;
import org.dasein.cloud.network.AbstractFirewallSupport;
import org.dasein.cloud.network.Direction;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallCapabilities;
import org.dasein.cloud.network.FirewallCreateOptions;
import org.dasein.cloud.network.FirewallRule;
import org.dasein.cloud.network.Permission;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.network.RuleTarget;
import org.dasein.cloud.network.RuleTargetType;
import org.dasein.cloud.util.APITrace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SecurityGroup extends AbstractFirewallSupport<CSCloud> {
    static private final Logger logger = Logger.getLogger(SecurityGroup.class);

    static public final String AUTHORIZE_SECURITY_GROUP_EGRESS  = "authorizeSecurityGroupEgress";
    static public final String AUTHORIZE_SECURITY_GROUP_INGRESS = "authorizeSecurityGroupIngress";
    static public final String CREATE_SECURITY_GROUP            = "createSecurityGroup";
    static public final String DELETE_SECURITY_GROUP            = "deleteSecurityGroup";
    static public final String LIST_SECURITY_GROUPS             = "listSecurityGroups";
    static public final String REVOKE_SECURITY_GROUP_EGRESS     = "revokeSecurityGroupEgress";
    static public final String REVOKE_SECURITY_GROUP_INGRESS    = "revokeSecurityGroupIngress";

    SecurityGroup(CSCloud provider) {
        super(provider);
    }

    @Override
    public @Nonnull String authorize(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull RuleTarget sourceEndpoint, @Nonnull Protocol protocol, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort, @Nonnegative int precedence) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Firewall.authorize");
        try {
            if( !permission.equals(Permission.ALLOW) ) {
                throw new OperationNotSupportedException("Only ALLOW rules are supported");
            }
            String sourceCidr = null;
            boolean group;

            if( direction.equals(Direction.INGRESS) ) {
                group = sourceEndpoint.getRuleTargetType().equals(RuleTargetType.GLOBAL);
                if( !group ) {
                    sourceCidr = sourceEndpoint.getCidr();
                }
            }
            else {
                group = destinationEndpoint.getRuleTargetType().equals(RuleTargetType.GLOBAL);
                if( !group ) {
                    sourceCidr = destinationEndpoint.getCidr();
                }
            }

            if( sourceCidr != null && sourceCidr.indexOf('/') == -1 ) {
                sourceCidr = sourceCidr + "/32";
            }
            Param[] params;
            CSMethod method = new CSMethod(getProvider());

            if( group ) {
                throw new OperationNotSupportedException("Security group sources are not supported");
            }
            else {
                params = new Param[] { new Param("securitygroupid", firewallId), new Param("cidrlist", sourceCidr), new Param("startport", String.valueOf(beginPort)), new Param("endport", String.valueOf(endPort)), new Param("protocol", protocol.name()) };
            }
            Document doc = null;
            if( direction.equals(Direction.INGRESS) ) {
                doc = method.get(method.buildUrl(AUTHORIZE_SECURITY_GROUP_INGRESS, params), AUTHORIZE_SECURITY_GROUP_INGRESS);
            }
            else {
                doc = method.get(method.buildUrl(AUTHORIZE_SECURITY_GROUP_EGRESS, params), AUTHORIZE_SECURITY_GROUP_EGRESS);
            }
            cloudstack.waitForJob(doc, "Authorize rule");

            getProvider().waitForJob(doc, "Authorize rule");
            String id = getRuleId(firewallId, direction, permission, protocol, sourceEndpoint, destinationEndpoint, beginPort, endPort);
            if( id == null ) {
                throw new CloudException("Unable to identify newly created firewall rule ID");
            }
            return id;
        }
        finally {
            APITrace.end();
        }
    }

    private @Nullable String getRuleId(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull Protocol protocol, @Nonnull RuleTarget sourceEndpoint, @Nonnull RuleTarget destinationEndpoint, int beginPort, int endPort) throws CloudException, InternalException {

        //<egressrule>
        // <ruleid>720e3652-49f9-4d45-a426-fbf55d3c10e0</ruleid>
        // <protocol>tcp</protocol>
        // <startport>83</startport>
        // <endport>83</endport>
        // <cidr>209.98.98.98/32</cidr>
        // </egressrule>
        for( FirewallRule rule : getRules(firewallId) ) {
            if( rule.getDirection().equals(direction) ) {
                if( rule.getPermission().equals(permission) ) {
                    if( rule.getProtocol().equals(protocol) ) {
                        if( rule.getSourceEndpoint().getRuleTargetType().equals(sourceEndpoint.getRuleTargetType()) ) {
                            if( rule.getDestinationEndpoint().getRuleTargetType().equals(destinationEndpoint.getRuleTargetType()) ) {
                                if( rule.getStartPort() == beginPort ) {
                                    if( rule.getEndPort() == endPort ) {
                                        if( rule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR) ) {
                                            //noinspection ConstantConditions
                                            if( rule.getSourceEndpoint().getCidr().equals(sourceEndpoint.getCidr()) ) {
                                                if( rule.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.CIDR) ) {
                                                    //noinspection ConstantConditions
                                                    if( rule.getDestinationEndpoint().getCidr().equals(destinationEndpoint.getCidr()) ) {
                                                        return rule.getProviderRuleId();
                                                    }
                                                }
                                                else if( rule.getDestinationEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL) ) {
                                                    //noinspection ConstantConditions
                                                    if( rule.getDestinationEndpoint().getProviderFirewallId().equals(destinationEndpoint.getProviderFirewallId()) ) {
                                                        return rule.getProviderRuleId();
                                                    }
                                                }
                                            }
                                        }
                                        else if( rule.getSourceEndpoint().getRuleTargetType().equals(RuleTargetType.GLOBAL) ) {
                                            //noinspection ConstantConditions
                                            if( rule.getSourceEndpoint().getProviderFirewallId().equals(sourceEndpoint.getProviderFirewallId()) ) {
                                                return rule.getProviderRuleId();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @Nonnull String create(@Nonnull FirewallCreateOptions options) throws InternalException, CloudException {
        if( options.getProviderVlanId() != null ) {
            throw new OperationNotSupportedException("No VLAN security groups are supported");
        }
        APITrace.begin(getProvider(), "Firewall.create");
        try {
            Param[] params = new Param[] { new Param("name", options.getName()), new Param("description", options.getDescription()) };
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl(CREATE_SECURITY_GROUP, params), CREATE_SECURITY_GROUP);
            NodeList matches = doc.getElementsByTagName("id");
            String groupId = null;

            if( matches.getLength() > 0 ) {
                groupId = matches.item(0).getFirstChild().getNodeValue();
            }
            if( groupId == null ) {
                throw new CloudException("Failed to create firewall");
            }
            return groupId;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void delete(@Nonnull String firewallId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.delete");
        try {
            try {
                for( FirewallRule rule : getRules(firewallId) ) {
                    try { revoke(rule.getProviderRuleId()); }
                    catch( Throwable ignore ) { }
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
            CSMethod method = new CSMethod(getProvider());

            method.get(method.buildUrl(DELETE_SECURITY_GROUP, new Param("id", firewallId)), DELETE_SECURITY_GROUP);
        }
        finally {
            APITrace.end();
        }
    }

    private transient volatile SecurityGroupCapabilities capabilities;
    @Nonnull
    @Override
    public FirewallCapabilities getCapabilities() throws CloudException, InternalException {
        if( capabilities == null ) {
            capabilities = new SecurityGroupCapabilities(getProvider());
        }
        return capabilities;
    }

    @Override
    public @Nullable Firewall getFirewall(@Nonnull String firewallId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.getFirewall");
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }
            CSMethod method = new CSMethod(getProvider());

            try {
                Document doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("id", firewallId)), LIST_SECURITY_GROUPS);
                NodeList matches = doc.getElementsByTagName("securitygroup");

                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        Firewall fw = toFirewall(node, ctx);

                        if( fw != null ) {
                            return fw;
                        }
                    }
                }
                return null;
            }
            catch( CSException e ) {
                if( e.getHttpCode() == 431 ) {
                    return null;
                }
                throw e;
            }
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull String getProviderTermForFirewall(@Nonnull Locale locale) {
        return "security group";
    }

    @Override
    public @Nonnull Collection<FirewallRule> getRules(@Nonnull String firewallId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.getRules");
        try {
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("id", firewallId)), LIST_SECURITY_GROUPS);
            ArrayList<FirewallRule> rules = new ArrayList<FirewallRule>();


            int numPages = 1;
            NodeList nodes = doc.getElementsByTagName("count");
            Node n = nodes.item(0);
            if (n != null) {
                String value = n.getFirstChild().getNodeValue().trim();
                int count = Integer.parseInt(value);
                numPages = count/500;
                int remainder = count % 500;
                if (remainder > 0) {
                    numPages++;
                }
            }

            for (int page = 1; page <= numPages; page++) {
                if (page > 1) {
                    String nextPage = String.valueOf(page);
                    doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("id", firewallId), new Param("pagesize", "500"), new Param("page", nextPage)), LIST_SECURITY_GROUPS);
                }
                NodeList matches = doc.getElementsByTagName("ingressrule");
                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        FirewallRule rule = toRule(firewallId, node, Direction.INGRESS);

                        if( rule != null ) {
                            rules.add(rule);
                        }
                    }
                }

                matches = doc.getElementsByTagName("egressrule");
                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        FirewallRule rule = toRule(firewallId, node, Direction.EGRESS);

                        if( rule != null ) {
                            rules.add(rule);
                        }
                    }
                }
            }
            return rules;
        }
        finally {
            APITrace.end();
        }
    }

    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Firewall.isSubscribed");
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }
            String regionId = ctx.getRegionId();

            if( regionId == null ) {
                throw new CloudException("No region was set for this request");
            }
            return getProvider().getDataCenterServices().supportsSecurityGroups(regionId, false);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Collection<Firewall> list() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.list");
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS), LIST_SECURITY_GROUPS);
            ArrayList<Firewall> firewalls = new ArrayList<Firewall>();

            int numPages = 1;
            NodeList nodes = doc.getElementsByTagName("count");
            Node n = nodes.item(0);
            if (n != null) {
                String value = n.getFirstChild().getNodeValue().trim();
                int count = Integer.parseInt(value);
                numPages = count/500;
                int remainder = count % 500;
                if (remainder > 0) {
                    numPages++;
                }
            }

            for (int page = 1; page <= numPages; page++) {
                if (page > 1) {
                    String nextPage = String.valueOf(page);
                    doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("pagesize", "500"), new Param("page", nextPage)), LIST_SECURITY_GROUPS);
                }
                NodeList matches = doc.getElementsByTagName("securitygroup");

                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        Firewall fw = toFirewall(node, ctx);

                        if( fw != null ) {
                            firewalls.add(fw);
                        }
                    }
                }
            }
            return firewalls;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listFirewallStatus() throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.listFirewallStatus");
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS), LIST_SECURITY_GROUPS);
            ArrayList<ResourceStatus> firewalls = new ArrayList<ResourceStatus>();

            int numPages = 1;
            NodeList nodes = doc.getElementsByTagName("count");
            Node n = nodes.item(0);
            if (n != null) {
                String value = n.getFirstChild().getNodeValue().trim();
                int count = Integer.parseInt(value);
                numPages = count/500;
                int remainder = count % 500;
                if (remainder > 0) {
                    numPages++;
                }
            }

            for (int page = 1; page <= numPages; page++) {
                if (page > 1) {
                    String nextPage = String.valueOf(page);
                    doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("pagesize", "500"), new Param("page", nextPage)), LIST_SECURITY_GROUPS);
                }
                NodeList matches = doc.getElementsByTagName("securitygroup");

                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        ResourceStatus fw = toStatus(node);

                        if( fw != null ) {
                            firewalls.add(fw);
                        }
                    }
                }
            }
            return firewalls;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void revoke(@Nonnull String providerFirewallRuleId) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "Firewall.revoke");
        try {
            FirewallRule target = null;

            for( Firewall fw : list() ) {
                //noinspection ConstantConditions
                for( FirewallRule rule : getRules(fw.getProviderFirewallId()) ) {
                    if( rule.getProviderRuleId().equals(providerFirewallRuleId) ) {
                        target = rule;
                        break;
                    }
                }
                if( target != null ) {
                    break;
                }
            }
            if( target == null ) {
                return;
            }
            Param[] params = new Param[] { new Param("id", providerFirewallRuleId) };
            CSMethod method = new CSMethod(getProvider());

            if( Direction.EGRESS.equals(target.getDirection()) ) {
                method.get(method.buildUrl(REVOKE_SECURITY_GROUP_EGRESS, params), REVOKE_SECURITY_GROUP_EGRESS);
            }
            else {
                method.get(method.buildUrl(REVOKE_SECURITY_GROUP_INGRESS, params), REVOKE_SECURITY_GROUP_INGRESS);
            }
        }
        finally {
            APITrace.end();
        }
    }

    public @Nonnull Iterable<String> listFirewallsForVM(@Nonnull String vmId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Firewall.listFirewallsForVM");
        try {
            ProviderContext ctx = getContext();

            if( ctx == null ) {
                throw new CloudException("No context was set for this request");
            }
            CSMethod method = new CSMethod(getProvider());
            Document doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("virtualmachineId", vmId)), LIST_SECURITY_GROUPS);
            ArrayList<String> firewalls = new ArrayList<String>();

            int numPages = 1;
            NodeList nodes = doc.getElementsByTagName("count");
            Node n = nodes.item(0);
            if (n != null) {
                String value = n.getFirstChild().getNodeValue().trim();
                int count = Integer.parseInt(value);
                numPages = count/500;
                int remainder = count % 500;
                if (remainder > 0) {
                    numPages++;
                }
            }

            for (int page = 1; page <= numPages; page++) {
                if (page > 1) {
                    String nextPage = String.valueOf(page);
                    doc = method.get(method.buildUrl(LIST_SECURITY_GROUPS, new Param("virtualmachineId", vmId), new Param("pagesize", "500"), new Param("page", nextPage)), LIST_SECURITY_GROUPS);
                }
                NodeList matches = doc.getElementsByTagName("securitygroup");

                for( int i=0; i<matches.getLength(); i++ ) {
                    Node node = matches.item(i);

                    if( node != null ) {
                        Firewall fw = toFirewall(node, ctx);

                        if( fw != null ) {
                            firewalls.add(fw.getProviderFirewallId());
                        }
                    }
                }
            }
            return firewalls;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, Direction.INGRESS, Permission.ALLOW, cidr, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull String cidr, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, direction, Permission.ALLOW, cidr, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, int beginPort, int endPort) throws CloudException, InternalException {
        revoke(firewallId, direction, permission, source, protocol, RuleTarget.getGlobal(firewallId), beginPort, endPort);
    }

    @Override
    public void revoke(@Nonnull String firewallId, @Nonnull Direction direction, @Nonnull Permission permission, @Nonnull String source, @Nonnull Protocol protocol, @Nonnull RuleTarget target, int beginPort, int endPort) throws CloudException, InternalException {
        if( !Permission.ALLOW.equals(permission) ) {
            throw new OperationNotSupportedException("Only ALLOW rules are supported");
        }
        RuleTarget sourceEndpoint, destinationEndpoint;

        if( direction.equals(Direction.INGRESS) ) {
            sourceEndpoint = RuleTarget.getCIDR(source);
            destinationEndpoint = target;
        }
        else {
            sourceEndpoint = target;
            destinationEndpoint = RuleTarget.getCIDR(source);
        }
        String ruleId = getRuleId(firewallId, direction, permission, protocol, sourceEndpoint, destinationEndpoint, beginPort, endPort);

        if( ruleId == null ) {
            logger.warn("No such rule for " + firewallId + ": " + direction + "/" + permission + "/" + source + "/" + protocol + "/" + beginPort + "/" + endPort);
            return;
        }
        revoke(ruleId);
    }

    @Override
    public boolean supportsFirewallSources() throws CloudException, InternalException {
        return false;
    }

    private @Nullable Firewall toFirewall(@Nullable Node node, @Nonnull ProviderContext ctx) throws CloudException, InternalException {
        if( node == null ) {
            return null;
        }
        String regionId = ctx.getRegionId();

        if( regionId == null ) {
            throw new CloudException("No region was specified for this request");
        }
        NodeList attributes = node.getChildNodes();
        Firewall firewall = new Firewall();
        
        firewall.setActive(true);
        firewall.setAvailable(true);
        firewall.setRegionId(regionId);
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
                value = null;
            }
            if( name.equalsIgnoreCase("id") && value != null ) {
                firewall.setProviderFirewallId(value);
            }
            else if( name.equalsIgnoreCase("description") && value != null ) {
                firewall.setDescription(value);
            }
            else if( name.equalsIgnoreCase("name") && value != null ) {
                firewall.setName(value);
            }
        }
        if( firewall.getProviderFirewallId() == null ) {
            logger.warn("Discovered firewall " + firewall.getProviderFirewallId() + " with an empty firewall ID");
            return null;
        }
        String id = firewall.getProviderFirewallId();
        String name;

        if( id == null ) {
            return null;
        }
        name = firewall.getName();
        if( name == null ) {
            name = id;
            firewall.setName(name);
        }
        if( firewall.getDescription() == null ) {
            firewall.setDescription(name);
        }
        return firewall;
    }
    
    private FirewallRule toRule(String firewallId, Node node, Direction direction) {
        if( node == null) {
            return null;
        }
        
        NodeList attributes = node.getChildNodes();
        int startPort = -1, endPort = -1;
        Protocol protocol = Protocol.TCP;
        String source = "0.0.0.0/0";
        String ruleId = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase();
            String value;
            
            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();                
            }
            else {
                value = null;
            }
            if( name.equalsIgnoreCase("cidr") && value != null ) {
                source = value;
            }
            else if( name.equalsIgnoreCase("endport") && value != null ) {
                endPort = Integer.parseInt(value);
            }
            else if( name.equalsIgnoreCase("startport") && value != null ) {
                startPort = Integer.parseInt(value);
            }
            else if( name.equalsIgnoreCase("protocol") && value != null ) {
                protocol = Protocol.valueOf(value.toUpperCase());
            }
            else if( name.equalsIgnoreCase("ruleId") && value != null ) {
                ruleId = value;
            }
        }
        if( (startPort == -1 || endPort == -1) && (startPort != -1 || endPort != -1) ) {
            if( startPort == -1 ) {
                startPort = endPort;
            }
            else {
                endPort = startPort;
            }
        }
        if( direction.equals(Direction.INGRESS) ) {
            return FirewallRule.getInstance(ruleId, firewallId, RuleTarget.getCIDR(source), direction, protocol, Permission.ALLOW, RuleTarget.getGlobal(firewallId), startPort, endPort);
        }
        else {
            return FirewallRule.getInstance(ruleId, firewallId, RuleTarget.getGlobal(firewallId), direction, protocol, Permission.ALLOW, RuleTarget.getCIDR(source), startPort, endPort);
        }
    }

    private @Nullable ResourceStatus toStatus(@Nullable Node node) throws CloudException, InternalException {
        if( node == null ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName().toLowerCase();
            String value;

            if( attribute.getChildNodes().getLength() > 0 ) {
                value = attribute.getFirstChild().getNodeValue();
            }
            else {
                value = null;
            }
            if( name.equalsIgnoreCase("id") && value != null ) {
                return new ResourceStatus(value, true);
            }
        }
        return null;
    }
}
