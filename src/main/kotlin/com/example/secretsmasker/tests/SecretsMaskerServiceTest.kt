import com.example.secretsmasker.SecretsMaskerService

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecretsMaskerServiceTest {

    @Test
    fun `test toggleMasking`() {
        val service = SecretsMaskerService()
        val initial = service.isMaskingEnabled
        val toggled = service.toggleMasking()
        assertNotEquals(initial, toggled)
    }

    @Test
    fun `test calculateValuePortion with equals sign`() {
        val text = "API_KEY=abc123"
        val pattern = Regex("API_KEY=.*")
        val matcher = pattern.toPattern().matcher(text)
        assertTrue(matcher.find())

        val (start, end) = SecretsMaskerService().run {
            calculateValuePortion(text, pattern.toPattern(), matcher)
        }

        assertEquals("abc123", text.substring(start, end))
    }

    @Test
    fun `test calculateValuePortion fallback full match`() {
        val text = "SECRETKEYabc123"
        val pattern = Regex("SECRETKEY.*")
        val matcher = pattern.toPattern().matcher(text)
        assertTrue(matcher.find())

        val (start, end) = SecretsMaskerService().run {
            calculateValuePortion(text, pattern.toPattern(), matcher)
        }

        assertEquals("SECRETKEYabc123", text.substring(start, end))
    }
}
