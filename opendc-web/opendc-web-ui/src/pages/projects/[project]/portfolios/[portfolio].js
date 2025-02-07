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

import { useRouter } from 'next/router'
import Head from 'next/head'
import React, { useRef } from 'react'
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
import { AppPage } from '../../../../components/AppPage'
import ContextSelectionSection from '../../../../components/context/ContextSelectionSection'
import PortfolioSelector from '../../../../components/context/PortfolioSelector'
import ProjectSelector from '../../../../components/context/ProjectSelector'
import BreadcrumbLink from '../../../../components/util/BreadcrumbLink'
import PortfolioOverview from '../../../../components/portfolios/PortfolioOverview'
import PortfolioResults from '../../../../components/portfolios/PortfolioResults'

/**
 * Page that displays the results in a portfolio.
 */
function Portfolio() {
    const router = useRouter()
    const { project: projectId, portfolio: portfolioId } = router.query

    const overviewRef = useRef(null)
    const resultsRef = useRef(null)

    const breadcrumb = (
        <Breadcrumb>
            <BreadcrumbItem to="/projects" component={BreadcrumbLink}>
                Projects
            </BreadcrumbItem>
            <BreadcrumbItem to={`/projects/${projectId}`} component={BreadcrumbLink}>
                Project details
            </BreadcrumbItem>
            <BreadcrumbItem to={`/projects/${projectId}/portfolios/${portfolioId}`} component={BreadcrumbLink} isActive>
                Portfolio
            </BreadcrumbItem>
        </Breadcrumb>
    )

    const contextSelectors = (
        <ContextSelectionSection>
            <ProjectSelector projectId={projectId} />
            <PortfolioSelector projectId={projectId} portfolioId={portfolioId} />
        </ContextSelectionSection>
    )

    return (
        <AppPage breadcrumb={breadcrumb} contextSelectors={contextSelectors}>
            <Head>
                <title>Portfolio - OpenDC</title>
            </Head>
            <PageSection variant={PageSectionVariants.light}>
                <TextContent>
                    <Text component="h1">Portfolio</Text>
                </TextContent>
            </PageSection>
            <PageSection type="none" variant={PageSectionVariants.light} className="pf-c-page__main-tabs" sticky="top">
                <Divider component="div" />
                <Tabs defaultActiveKey={0} className="pf-m-page-insets">
                    <Tab
                        eventKey={0}
                        title={<TabTitleText>Overview</TabTitleText>}
                        tabContentId="overview"
                        tabContentRef={overviewRef}
                    />
                    <Tab
                        eventKey={1}
                        title={<TabTitleText>Results</TabTitleText>}
                        tabContentId="results"
                        tabContentRef={resultsRef}
                    />
                </Tabs>
            </PageSection>
            <PageSection isFilled>
                <TabContent eventKey={0} id="overview" ref={overviewRef} aria-label="Overview tab">
                    <PortfolioOverview portfolioId={portfolioId} />
                </TabContent>
                <TabContent eventKey={1} id="results" ref={resultsRef} aria-label="Results tab" hidden>
                    <PortfolioResults portfolioId={portfolioId} />
                </TabContent>
            </PageSection>
        </AppPage>
    )
}

export default Portfolio
