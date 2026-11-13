import unittest
from release_version import release_version


class ReleaseVersionTest(unittest.TestCase):
    def test_alpha_and_beta_preserve_display_and_tag(self):
        for suffix, channel in [("α", "alpha"), ("β", "beta")]:
            with self.subTest(suffix=suffix):
                value = release_version("2.0.0" + suffix, 23)
                self.assertEqual(value["tag"], "v2.0.0" + suffix)
                self.assertEqual(value["version"], "2.0.0" + suffix)
                self.assertEqual(value["internalVersion"], "2.0.0-" + channel)
                self.assertEqual(value["channel"], channel)
                self.assertTrue(value["prerelease"])

    def test_formal_release_keeps_stable_channel(self):
        value = release_version("2.0.0", 24)
        self.assertFalse(value["prerelease"])
        self.assertEqual(value["internalVersion"], "2.0.0")
        self.assertEqual(value["channel"], "stable")

    def test_invalid_names_cannot_inject_workflow_outputs_or_lose_suffix(self):
        for name in ["2.0.0alpha", "v2.0.0α", "02.0.0", "2.0.0α1", "2.0", "2.0.0\nprerelease=false"]:
            with self.subTest(name=name):
                with self.assertRaises(ValueError):
                    release_version(name, 23)

    def test_code_remains_a_valid_positive_android_integer(self):
        for code in [0, -1, "01", "23\ncode=1", 2100000001]:
            with self.subTest(code=code):
                with self.assertRaises(ValueError):
                    release_version("2.0.0α", code)


if __name__ == "__main__":
    unittest.main()
