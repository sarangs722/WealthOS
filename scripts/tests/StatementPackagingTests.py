import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'core/database/schemas/com.wealthos.database.BankDatabase'
spec = importlib.util.spec_from_file_location('statement_packaging', ROOT / 'scripts/Verify-StatementPackaging.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StatementPackagingTests(unittest.TestCase):
    def fixture(self, directory, stale=False, missing_identity=False, test_data=False):
        path = Path(directory) / 'fictional-container.apk'
        with ZipFile(path, 'w') as archive:
            for schema in SCHEMAS.glob('*.json'):
                data = json.loads(schema.read_text(encoding='utf-8'))
                if stale and schema.stem == '8':
                    table = next(t for t in data['database']['entities'] if t['tableName'] == 'statement_imports')
                    table['fields'] = [f for f in table['fields'] if f['fieldPath'] != 'currency']
                archive.writestr('assets/com.wealthos.database.BankDatabase/' + schema.name, json.dumps(data))
            identity = json.loads((SCHEMAS / '8.json').read_text(encoding='utf-8'))['database']['identityHash']
            archive.writestr('classes.dex', b'fictional test container' if missing_identity else identity.encode('ascii'))
            archive.writestr('assets/licenses/statement-reader.txt', 'Fictional notice fixture')
            if test_data:
                archive.writestr('assets/statements/fictional.csv', 'Fictional test-only data')
        return path

    def test_matching_assets_are_accepted(self):
        with tempfile.TemporaryDirectory(prefix='wealthos-packaging-test-') as directory:
            self.assertEqual(8, module.verify(self.fixture(directory), SCHEMAS)['schema'])

    def test_stale_schema_reproduces_incremental_packaging_failure(self):
        with tempfile.TemporaryDirectory(prefix='wealthos-packaging-test-') as directory:
            with self.assertRaises(ValueError):
                module.verify(self.fixture(directory, stale=True), SCHEMAS)

    def test_old_compiled_room_identity_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix='wealthos-packaging-test-') as directory:
            with self.assertRaises(ValueError):
                module.verify(self.fixture(directory, missing_identity=True), SCHEMAS)

    def test_statement_fixtures_cannot_ship_in_application(self):
        with tempfile.TemporaryDirectory(prefix='wealthos-packaging-test-') as directory:
            with self.assertRaises(ValueError):
                module.verify(self.fixture(directory, test_data=True), SCHEMAS)


if __name__ == '__main__':
    unittest.main()
