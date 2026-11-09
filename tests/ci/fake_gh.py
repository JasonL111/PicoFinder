"""Minimal fake CLI for black-box publication tests; never contacts GitHub."""
import json
import os
from pathlib import Path
import sys

path = Path(os.environ['GH_FAKE_STATE'])
state = json.loads(path.read_text())
args = sys.argv[1:]
state['calls'].append(args)
status = 0
out = ''
error = ''
if args[0] == 'api':
    method = args[args.index('--method') + 1] if '--method' in args else 'GET'
    endpoint = next(arg for arg in args if arg.startswith('repos/'))
    if method == 'GET' and endpoint.endswith('git/ref/heads/main'):
        out = state['head']
    elif method == 'GET' and '/releases/tags/' in endpoint:
        if state.get('denied'):
            status, error = 1, 'gh: Forbidden (HTTP 403)'
        elif state['release']:
            out = json.dumps({'id': 1, 'draft': state.get('draft', False)})
        else:
            status, error = 1, 'gh: Not Found (HTTP 404)'
    elif method == 'GET' and '/git/ref/tags/' in endpoint:
        if state['tag']:
            out = json.dumps({'object': {'sha': state['tag']}})
        else:
            status, error = 1, 'gh: Not Found (HTTP 404)'
    elif method in ('PATCH', 'POST'):
        state['tag'] = next(arg[4:] for arg in args if arg.startswith('sha='))
        out = '{}'
    else:
        status, error = 2, 'Unhandled API call'
elif args[:2] == ['release', 'create']:
    state['release'] = True
elif args[:2] == ['release', 'upload']:
    state['uploaded'] = [arg for arg in args if arg.endswith(('.apk', '.json', 'SHA256SUMS'))]
elif args[:2] == ['release', 'edit']:
    state['published'] = '--draft=false' in args
else:
    status, error = 2, 'Unhandled CLI call'
path.write_text(json.dumps(state))
if out: print(out)
if error: print(error, file=sys.stderr)
raise SystemExit(status)
