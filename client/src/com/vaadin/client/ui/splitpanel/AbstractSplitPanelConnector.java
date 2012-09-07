/*
 * Copyright 2011 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.ui.splitpanel;

import java.util.LinkedList;
import java.util.List;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.DomEvent.Type;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.client.LayoutManager;
import com.vaadin.client.communication.RpcProxy;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.client.ui.AbstractComponentContainerConnector;
import com.vaadin.client.ui.ClickEventHandler;
import com.vaadin.client.ui.SimpleManagedLayout;
import com.vaadin.client.ui.splitpanel.VAbstractSplitPanel.SplitterMoveHandler;
import com.vaadin.client.ui.splitpanel.VAbstractSplitPanel.SplitterMoveHandler.SplitterMoveEvent;
import com.vaadin.shared.MouseEventDetails;
import com.vaadin.shared.ui.ComponentStateUtil;
import com.vaadin.shared.ui.splitpanel.AbstractSplitPanelRpc;
import com.vaadin.shared.ui.splitpanel.AbstractSplitPanelState;
import com.vaadin.shared.ui.splitpanel.AbstractSplitPanelState.SplitterState;

public abstract class AbstractSplitPanelConnector extends
        AbstractComponentContainerConnector implements SimpleManagedLayout {

    private AbstractSplitPanelRpc rpc;

    @Override
    protected void init() {
        super.init();
        rpc = RpcProxy.create(AbstractSplitPanelRpc.class, this);
        // TODO Remove
        getWidget().client = getConnection();

        getWidget().addHandler(new SplitterMoveHandler() {

            @Override
            public void splitterMoved(SplitterMoveEvent event) {
                String position = getWidget().getSplitterPosition();
                float pos = 0;
                if (position.indexOf("%") > 0) {
                    // Send % values as a fraction to avoid that the splitter
                    // "jumps" when server responds with the integer pct value
                    // (e.g. dragged 16.6% -> should not jump to 17%)
                    pos = Float.valueOf(position.substring(0,
                            position.length() - 1));
                } else {
                    pos = Integer.parseInt(position.substring(0,
                            position.length() - 2));
                }

                rpc.setSplitterPosition(pos);
            }

        }, SplitterMoveEvent.TYPE);
    }

    @Override
    public void updateCaption(ComponentConnector component) {
        // TODO Implement caption handling
    }

    ClickEventHandler clickEventHandler = new ClickEventHandler(this) {

        @Override
        protected <H extends EventHandler> HandlerRegistration registerHandler(
                H handler, Type<H> type) {
            if ((Event.getEventsSunk(getWidget().splitter) & Event
                    .getTypeInt(type.getName())) != 0) {
                // If we are already sinking the event for the splitter we do
                // not want to additionally sink it for the root element
                return getWidget().addHandler(handler, type);
            } else {
                return getWidget().addDomHandler(handler, type);
            }
        }

        @Override
        protected boolean shouldFireEvent(DomEvent<?> event) {
            Element target = event.getNativeEvent().getEventTarget().cast();
            if (!getWidget().splitter.isOrHasChild(target)) {
                return false;
            }

            return super.shouldFireEvent(event);
        };

        @Override
        protected Element getRelativeToElement() {
            return getWidget().splitter;
        };

        @Override
        protected void fireClick(NativeEvent event,
                MouseEventDetails mouseDetails) {
            rpc.splitterClick(mouseDetails);
        }

    };

    @Override
    public void onStateChanged(StateChangeEvent stateChangeEvent) {
        super.onStateChanged(stateChangeEvent);

        getWidget().immediate = getState().immediate;

        getWidget().setEnabled(isEnabled());

        clickEventHandler.handleEventHandlerRegistration();

        if (ComponentStateUtil.hasStyles(getState())) {
            getWidget().componentStyleNames = getState().styles;
        } else {
            getWidget().componentStyleNames = new LinkedList<String>();
        }

        // Splitter updates
        SplitterState splitterState = getState().splitterState;

        getWidget().setStylenames();

        getWidget().minimumPosition = splitterState.minPosition
                + splitterState.minPositionUnit;

        getWidget().maximumPosition = splitterState.maxPosition
                + splitterState.maxPositionUnit;

        getWidget().position = splitterState.position
                + splitterState.positionUnit;

        getWidget().setPositionReversed(splitterState.positionReversed);

        getWidget().setLocked(splitterState.locked);

        // This is needed at least for cases like #3458 to take
        // appearing/disappearing scrollbars into account.
        getConnection().runDescendentsLayout(getWidget());

        getLayoutManager().setNeedsLayout(this);

        getWidget().makeScrollable();
    }

    @Override
    public void layout() {
        VAbstractSplitPanel splitPanel = getWidget();
        splitPanel.setSplitPosition(splitPanel.position);
        splitPanel.updateSizes();
        // Report relative sizes in other direction for quicker propagation
        List<ComponentConnector> children = getChildComponents();
        for (ComponentConnector child : children) {
            reportOtherDimension(child);
        }
    }

    private void reportOtherDimension(ComponentConnector child) {
        LayoutManager layoutManager = getLayoutManager();
        if (this instanceof HorizontalSplitPanelConnector) {
            if (child.isRelativeHeight()) {
                int height = layoutManager.getInnerHeight(getWidget()
                        .getElement());
                layoutManager.reportHeightAssignedToRelative(child, height);
            }
        } else {
            if (child.isRelativeWidth()) {
                int width = layoutManager.getInnerWidth(getWidget()
                        .getElement());
                layoutManager.reportWidthAssignedToRelative(child, width);
            }
        }
    }

    @Override
    public VAbstractSplitPanel getWidget() {
        return (VAbstractSplitPanel) super.getWidget();
    }

    @Override
    public AbstractSplitPanelState getState() {
        return (AbstractSplitPanelState) super.getState();
    }

    private ComponentConnector getFirstChild() {
        return (ComponentConnector) getState().firstChild;
    }

    private ComponentConnector getSecondChild() {
        return (ComponentConnector) getState().secondChild;
    }

    @Override
    public void onConnectorHierarchyChange(ConnectorHierarchyChangeEvent event) {
        super.onConnectorHierarchyChange(event);

        Widget newFirstChildWidget = null;
        if (getFirstChild() != null) {
            newFirstChildWidget = getFirstChild().getWidget();
        }
        getWidget().setFirstWidget(newFirstChildWidget);

        Widget newSecondChildWidget = null;
        if (getSecondChild() != null) {
            newSecondChildWidget = getSecondChild().getWidget();
        }
        getWidget().setSecondWidget(newSecondChildWidget);
    }
}