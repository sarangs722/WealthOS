"""Verify trusted financial schemas and compiled Room identity in a built APK. No financial records are read."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(apk, schemas):
    with ZipFile(apk) as archive:
        expected = sorted(Path(schemas).glob('*.json'), key=lambda path: int(path.stem))
        require({6, 7, 8}.issubset({int(path.stem) for path in expected}), 'Required trusted backup schemas are missing')
        for path in expected:
            name = 'assets/com.wealthos.database.BankDatabase/' + path.name
            require(json.loads(archive.read(name)) == json.loads(path.read_text(encoding='utf-8')), 'Packaged schema differs from trusted source')
        current = json.loads(expected[-1].read_text(encoding='utf-8'))['database']
        imports = next(table for table in current['entities'] if table['tableName'] == 'statement_imports')
        require(any(field['fieldPath'] == 'currency' and field['notNull'] for field in imports['fields']), 'Statement native currency is missing')
        identity = current['identityHash'].encode('ascii')
        require(any(identity in archive.read(name) for name in archive.namelist() if name.startswith('classes') and name.endswith('.dex')), 'Compiled Room identity differs from packaged schema')
        require('assets/licenses/statement-reader.txt' in archive.namelist(), 'Reader licenses are missing')
        require(not any(name.startswith('assets/statements/') for name in archive.namelist()), 'Test statement fixtures must not ship in the application')
        return {'status': 'SCHEMA_ASSETS_VERIFIED', 'schema': current['version'], 'tables': len(current['entities']), 'schemaFiles': len(expected), 'compiledRoomIdentity': 'matched', 'readerNotices': 'present', 'testStatements': 'absent'}


if __name__ == '__main__':
    try:
        schema_dir = Path(__file__).resolve().parents[1] / 'core/database/schemas/com.wealthos.database.BankDatabase'
        print(json.dumps(verify(sys.argv[1], schema_dir)))
    except Exception:
        print('Financial schema packaging verification failed.', file=sys.stderr)
        sys.exit(1)
