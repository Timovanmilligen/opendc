import PropTypes from 'prop-types'
import React from 'react'
import Link from 'next/link'
import { Project, Status } from '../../shapes'
import { Table, TableBody, TableHeader } from '@patternfly/react-table'
import { parseAndFormatDateTime } from '../../util/date-time'
import { AUTH_DESCRIPTION_MAP, AUTH_ICON_MAP } from '../../util/authorizations'
import { useAuth } from '../../auth'
import TableEmptyState from '../util/TableEmptyState'

const ProjectTable = ({ status, projects, onDelete, isFiltering }) => {
    const { user } = useAuth()
    const columns = ['Project name', 'Last edited', 'Access Rights']
    const rows =
        projects.length > 0
            ? projects.map((project) => {
                  const { level } = project.authorizations.find((auth) => auth.userId === user.sub)
                  const Icon = AUTH_ICON_MAP[level]
                  return [
                      {
                          title: <Link href={`/projects/${project._id}`}>{project.name}</Link>,
                      },
                      parseAndFormatDateTime(project.datetimeLastEdited),
                      {
                          title: (
                              <>
                                  <Icon className="pf-u-mr-md" key="auth" /> {AUTH_DESCRIPTION_MAP[level]}
                              </>
                          ),
                      },
                  ]
              })
            : [
                  {
                      heightAuto: true,
                      cells: [
                          {
                              props: { colSpan: 3 },
                              title: (
                                  <TableEmptyState
                                      status={status}
                                      loadingTitle="Loading Projects"
                                      isFiltering={isFiltering}
                                  />
                              ),
                          },
                      ],
                  },
              ]

    const actions =
        projects.length > 0
            ? [
                  {
                      title: 'Delete Project',
                      onClick: (_, rowId) => onDelete(projects[rowId]),
                  },
              ]
            : []

    return (
        <Table aria-label="Project List" variant="compact" cells={columns} rows={rows} actions={actions}>
            <TableHeader />
            <TableBody />
        </Table>
    )
}

ProjectTable.propTypes = {
    status: Status.isRequired,
    isFiltering: PropTypes.bool,
    projects: PropTypes.arrayOf(Project).isRequired,
    onDelete: PropTypes.func,
}

export default ProjectTable
