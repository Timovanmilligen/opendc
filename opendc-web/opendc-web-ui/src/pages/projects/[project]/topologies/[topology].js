/*
 * Copyright (c) 2021 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import dynamic from 'next/dynamic'
import { useRouter } from 'next/router'
import ContextSelectionSection from '../../../../components/context/ContextSelectionSection'
import ProjectSelector from '../../../../components/context/ProjectSelector'
import TopologySelector from '../../../../components/context/TopologySelector'
import TopologyOverview from '../../../../components/topologies/TopologyOverview'
import { useProject } from '../../../../data/project'
import { useDispatch } from 'react-redux'
import React, { useEffect, useState } from 'react'
import Head from 'next/head'
import { AppPage } from '../../../../components/AppPage'
import {
    Breadcrumb,
    BreadcrumbItem,
    Divider,
    PageSection,
    PageSectionVariants,
    Tab,
    TabContent,
    Tabs,
    TabTitleText,
    Text,
    TextContent,
} from '@patternfly/react-core'
import BreadcrumbLink from '../../../../components/util/BreadcrumbLink'
import { goToRoom } from '../../../../redux/actions/interaction-level'
import { openTopology } from '../../../../redux/actions/topology'

const TopologyMap = dynamic(() => import('../../../../components/topologies/TopologyMap'))

/**
 * Page that displays a datacenter topology.
 */
function Topology() {
    const router = useRouter()
    const { project: projectId, topology: topologyId } = router.query

    const { data: project } = useProject(projectId)

    const dispatch = useDispatch()
    useEffect(() => {
        if (topologyId) {
            dispatch(openTopology(topologyId))
        }
    }, [topologyId, dispatch])

    const [activeTab, setActiveTab] = useState('overview')

    const breadcrumb = (
        <Breadcrumb>
            <BreadcrumbItem to="/projects" component={BreadcrumbLink}>
                Projects
            </BreadcrumbItem>
            <BreadcrumbItem to={`/projects/${projectId}`} component={BreadcrumbLink}>
                Project details
            </BreadcrumbItem>
            <BreadcrumbItem to={`/projects/${projectId}/topologies/${topologyId}`} component={BreadcrumbLink} isActive>
                Topology
            </BreadcrumbItem>
        </Breadcrumb>
    )

    const contextSelectors = (
        <ContextSelectionSection>
            <ProjectSelector projectId={projectId} />
            <TopologySelector projectId={projectId} topologyId={topologyId} />
        </ContextSelectionSection>
    )

    return (
        <AppPage breadcrumb={breadcrumb} contextSelectors={contextSelectors}>
            <Head>
                <title>{project?.name ?? 'Topologies'} - OpenDC</title>
            </Head>
            <PageSection variant={PageSectionVariants.light}>
                <TextContent>
                    <Text component="h1">Topology</Text>
                </TextContent>
            </PageSection>
            <PageSection type="none" variant={PageSectionVariants.light} className="pf-c-page__main-tabs" sticky="top">
                <Divider component="div" />
                <Tabs
                    activeKey={activeTab}
                    onSelect={(_, tabIndex) => setActiveTab(tabIndex)}
                    className="pf-m-page-insets"
                >
                    <Tab eventKey="overview" title={<TabTitleText>Overview</TabTitleText>} tabContentId="overview" />
                    <Tab
                        eventKey="floor-plan"
                        title={<TabTitleText>Floor Plan</TabTitleText>}
                        tabContentId="floor-plan"
                    />
                </Tabs>
            </PageSection>
            <PageSection padding={activeTab === 'floor-plan' && { default: 'noPadding' }} isFilled>
                <TabContent id="overview" aria-label="Overview tab" hidden={activeTab !== 'overview'}>
                    <TopologyOverview
                        topologyId={topologyId}
                        onSelect={(type, obj) => {
                            if (type === 'room') {
                                dispatch(goToRoom(obj._id))
                                setActiveTab('floor-plan')
                            }
                        }}
                    />
                </TabContent>
                <TabContent id="floor-plan" aria-label="Floor Plan tab" className="pf-u-h-100" hidden={activeTab !== 'floor-plan'}>
                    <TopologyMap />
                </TabContent>
            </PageSection>
        </AppPage>
    )
}

export default Topology
