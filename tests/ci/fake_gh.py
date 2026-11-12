"""Black-box publication model; no network or real GitHub mutations."""
import hashlib
import json
import os
from pathlib import Path
import sys

path = Path(os.environ['GH_FAKE_STATE'])
state = json.loads(path.read_text())
args = sys.argv[1:]
state['calls'].append(args)
status, out, error = 0, '', ''


def release_record():
    return {'id': 7, 'tag_name': 'v1.0.0', 'draft': state.get('draft', False),
            'target_commitish': state.get('target', 'a' * 40), 'assets': state.get('assets', [])}


if args[0] == 'api':
    endpoint = args[1]
    if state.get('denied'):
        status, error = 1, 'gh: Forbidden (HTTP 403)'
    elif '/git/ref/tags/' in endpoint:
        if state.get('tag'):
            out = json.dumps({'object': {'sha': state['tag'], 'type': state.get('tag_type', 'commit')}})
        else:
            status, error = 1, 'gh: Not Found (HTTP 404)'
    elif '/git/tags/' in endpoint:
        out = json.dumps({'object': {'sha': state.get('peeled', 'a' * 40), 'type': 'commit'}})
    elif '/releases/tags/' in endpoint:
        # GitHub's tag lookup does not expose unpublished drafts, even after upload.
        if state.get('release') and not state.get('draft'):
            out = json.dumps(release_record())
        else:
            status, error = 1, 'gh: Not Found (HTTP 404)'
    elif endpoint.endswith('/releases?per_page=100'):
        assert '--paginate' in args and args[args.index('--jq') + 1] == '.[] | @json'
        if state.get('denied_release_list'):
            status, error = 1, 'gh: Forbidden (HTTP 403)'
        else:
            pages = []
            if state.get('release_page') == 2:
                pages.append([{'id': 6, 'tag_name': 'v0.9.0', 'draft': False}])
            items = [release_record()] if state.get('release') else []
            if state.get('duplicate_draft'):
                items.append(dict(release_record(), id=8))
            pages.append(items)
            out = '\n'.join(json.dumps(item) for page in pages for item in page)
    elif endpoint.endswith('/releases/7') and state.get('release'):
        out = json.dumps(release_record())
    else:
        status, error = 2, 'Unhandled API call'
elif args[:2] == ['release', 'create']:
    assert '--verify-tag' in args and '--draft' in args
    state.update(release=True, draft=True, target=args[args.index('--target') + 1])
elif args[:2] == ['release', 'upload']:
    assert '--clobber' not in args
    assets = state.setdefault('assets', [])
    for name in args[3:]:
        file = Path(name)
        if file.is_file():
            assets.append({'name': file.name, 'digest': 'sha256:' + hashlib.sha256(file.read_bytes()).hexdigest()})
    if state.get('corrupt_upload'):
        assets[0]['digest'] = 'sha256:' + '0' * 64
elif args[:2] == ['release', 'edit']:
    state['published'] = '--draft=false' in args
    state['draft'] = not state['published']
else:
    status, error = 2, 'Unhandled CLI call'
path.write_text(json.dumps(state))
if out:
    print(out)
if error:
    print(error, file=sys.stderr)
raise SystemExit(status)
