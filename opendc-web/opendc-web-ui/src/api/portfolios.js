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

import { request } from './index'

export function fetchPortfolio(auth, portfolioId) {
    return request(auth, `portfolios/${portfolioId}`)
}

export function fetchPortfoliosOfProject(auth, projectId) {
    return request(auth, `projects/${projectId}/portfolios`)
}

export function addPortfolio(auth, portfolio) {
    return request(auth, `projects/${portfolio.projectId}/portfolios`, 'POST', { portfolio })
}

export function updatePortfolio(auth, portfolioId, portfolio) {
    return request(auth, `portfolios/${portfolioId}`, 'PUT', { portfolio })
}

export function deletePortfolio(auth, portfolioId) {
    return request(auth, `portfolios/${portfolioId}`, 'DELETE')
}
