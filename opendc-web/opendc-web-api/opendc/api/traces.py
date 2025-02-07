#  Copyright (c) 2021 AtLarge Research
#
#  Permission is hereby granted, free of charge, to any person obtaining a copy
#  of this software and associated documentation files (the "Software"), to deal
#  in the Software without restriction, including without limitation the rights
#  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
#  copies of the Software, and to permit persons to whom the Software is
#  furnished to do so, subject to the following conditions:
#
#  The above copyright notice and this permission notice shall be included in all
#  copies or substantial portions of the Software.
#
#  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
#  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
#  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
#  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
#  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
#  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
#  SOFTWARE.

from flask_restful import Resource

from opendc.exts import requires_auth
from opendc.models.trace import Trace as TraceModel, TraceSchema


class TraceList(Resource):
    """
    Resource for the list of traces to pick from.
    """
    method_decorators = [requires_auth]

    def get(self):
        """Get all available Traces."""
        traces = TraceModel.get_all()
        data = TraceSchema().dump(traces.obj, many=True)
        return {'data': data}


class Trace(Resource):
    """
    Resource representing a single trace.
    """
    method_decorators = [requires_auth]

    def get(self, trace_id):
        """Get trace information by identifier."""
        trace = TraceModel.from_id(trace_id)
        trace.check_exists()
        data = TraceSchema().dump(trace.obj)
        return {'data': data}
