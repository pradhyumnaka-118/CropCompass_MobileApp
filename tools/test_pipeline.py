import tempfile
import unittest
from pathlib import Path
from build_karnataka_data import build, unit_factor, parse_date, fixture
from geocode_mandis import valid

class PipelineTest(unittest.TestCase):
    def test_units_dates_coordinates(self):
        self.assertEqual(unit_factor('Rs/Kg'),100)
        self.assertEqual(unit_factor('Rs/Quintal'),1)
        self.assertIsNone(unit_factor(''))
        self.assertEqual(parse_date('31/10/2026'),'2026-10-31')
        self.assertIsNone(parse_date('nonsense'))
        self.assertFalse(valid(20,77)); self.assertTrue(valid(13,77))
    def test_fixture_reproducible_and_bounded(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); (base/'raw').mkdir()
            a=build(base/'raw',base/'assets')
            b=build(base/'raw',base/'assets')
            self.assertEqual(a['dataSource'],'fixture')
            self.assertEqual(a['dataVersion'],b['dataVersion'])
            self.assertGreater(a['recordCount'],1000)
            self.assertLess((base/'assets/prices.csv').stat().st_size,30*1024*1024)
    def test_real_filter_invalid_units_and_duplicate(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); fixture(base/'raw')
            path=base/'raw/synthetic_test_prices.csv'
            import pandas as pd
            frame=pd.read_csv(path).head(5)
            frame.loc[0,'state_name']='Other State'
            frame.loc[1,'price_unit']='unknown'
            frame.loc[2,'modal_price']=-2
            frame.loc[3,'price_unit']='Rs/Kg'
            frame=pd.concat([frame,frame.iloc[[4]]]); frame.to_csv(path,index=False)
            metadata=build(base/'raw',base/'out')
            self.assertEqual(metadata['recordCount'],2)
            rows=pd.read_csv(base/'out/prices.csv')
            self.assertTrue((rows.modalPrice>0).all())
            self.assertTrue((rows.modalPrice>100000).any())
    def test_district_alias_does_not_create_a_duplicate_district(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);fixture(base/'raw')
            import pandas as pd,json
            path=base/'raw/synthetic_test_prices.csv'
            frame=pd.read_csv(path).head(5);frame['district_name']='Mysore';frame.to_csv(path,index=False)
            build(base/'raw',base/'out')
            districts=json.loads((base/'out/districts.json').read_text(encoding='utf-8'))
            markets=json.loads((base/'out/mandis.json').read_text(encoding='utf-8'))
            self.assertEqual(len(districts),31);self.assertEqual(markets[0]['districtId'],'mysuru')
    def test_missing_names_are_rejected_and_unicode_ids_are_safe(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);fixture(base/'raw')
            import pandas as pd,json
            path=base/'raw/synthetic_test_prices.csv';frame=pd.read_csv(path).head(3)
            frame.loc[0,'commodity']=' ';frame.loc[1,'commodity']='ಟೊಮೇಟೊ';frame.to_csv(path,index=False)
            metadata=build(base/'raw',base/'out')
            crops=json.loads((base/'out/commodities.json').read_text(encoding='utf-8'))
            self.assertEqual(metadata['recordCount'],2)
            self.assertTrue(all(crop['id'] for crop in crops))
            self.assertTrue(any(crop['nameEn']=='ಟೊಮೇಟೊ' and crop['id'].startswith('u-') for crop in crops))

if __name__=='__main__': unittest.main()
